import java.io.*;

// Classe responsavel pelas operacoes de CRUD no arquivo binario sequencial.
// A partir do TP2, o DAO tambem mantem um indice em Arvore B+ (campo id) coerente
// com o arquivo de dados: toda insercao/atualizacao/remocao reflete no indice.
public class FilmeDAO {
    private String caminhoArquivo;
    private BPlusTree indice; // pode ser null (mantem compatibilidade com o CRUD sequencial do TP1)

    public FilmeDAO(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
        this.indice = null;
    }

    // construtor novo do TP2: recebe o indice ja aberto/carregado, para mante-lo atualizado a cada operacao
    public FilmeDAO(String caminhoArquivo, BPlusTree indice) {
        this.caminhoArquivo = caminhoArquivo;
        this.indice = indice;
    }

    // busca um filme pelo id, percorrendo o arquivo sequencialmente do inicio ao fim (O(n))
    public Filme lerPorId(int id) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(4); // pula o cabecalho (ultimo id usado)
            
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                Filme filme = Filme.fromByteArray(dados);
                if (filme.getId() == id && lapide == 0) {
                    return filme; // achou o registro certo e ele esta valido
                }
            }
        }
        return null; // nao encontrou (ou o registro estava deletado)
    }

    // NOVO no TP2: busca um filme pelo id usando o indice em Arvore B+ (O(log n)).
    // Se nenhum indice foi associado a este DAO, cai de volta para a busca sequencial.
    public Filme lerPorIdIndexado(int id) throws IOException {
        if (indice == null) {
            return lerPorId(id);
        }

        long posicao = indice.buscar(id);
        if (posicao == -1) {
            return null; // id nao esta indexado
        }

        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(posicao);
            byte lapide = raf.readByte();
            if (lapide == 1) {
                return null; // registro foi deletado (indice desatualizado nao deveria acontecer, mas por seguranca)
            }
            int tamanho = raf.readInt();
            byte[] dados = new byte[tamanho];
            raf.readFully(dados);
            return Filme.fromByteArray(dados);
        }
    }

    // cria um novo filme, gerando o id automaticamente a partir do cabecalho
    public int criarFilme(Filme filme) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {

            raf.seek(0);
            int ultimoId = raf.readInt();
            int novoId = ultimoId + 1;
            
            filme.setId(novoId);
            byte[] dados = filme.toByteArray();
            
            // o novo registro sempre e escrito no final do arquivo
            raf.seek(raf.length());
            long posicaoRegistro = raf.getFilePointer(); // posicao que vai para o indice
            raf.writeByte(0); // lapide 0 = valido
            raf.writeInt(dados.length);
            raf.write(dados);
            
            // atualiza o cabecalho com o novo ultimo id usado
            raf.seek(0);
            raf.writeInt(novoId);

            // mantem o indice coerente com o arquivo de dados
            if (indice != null) {
                indice.inserir(novoId, posicaoRegistro);
            }
            
            return novoId;
        }
    }

    // atualiza um filme existente, procurando pelo id
    public boolean atualizarFilme(Filme filmeAtualizado) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {
            raf.seek(4);
            
            while (raf.getFilePointer() < raf.length()) {
                long posicaoLapide = raf.getFilePointer();
                byte lapideAntiga = raf.readByte();
                int tamanhoAntigo = raf.readInt();
                
                if (lapideAntiga == 0) { 
                    byte[] dadosAntigos = new byte[tamanhoAntigo];
                    raf.readFully(dadosAntigos);
                    
                    Filme filmeAntigo = Filme.fromByteArray(dadosAntigos);
                    
                    if (filmeAntigo.getId() == filmeAtualizado.getId()) {
                        byte[] dadosNovos = filmeAtualizado.toByteArray();
                        
                        // caso 1: o registro novo ocupa o mesmo espaco do antigo -> sobrescreve no lugar
                        // a posicao do registro nao muda, entao o indice nao precisa ser tocado
                        if (dadosNovos.length == tamanhoAntigo) {
                            raf.seek(posicaoLapide + 5); // pula lapide (1 byte) + tamanho (4 bytes)
                            raf.write(dadosNovos);
                            return true;
                        }
                        // caso 2: o tamanho mudou -> marca o registro antigo como deletado
                        // e escreve o novo no final do arquivo (a posicao muda -> indice precisa ser atualizado)
                        else {
                            raf.seek(posicaoLapide);
                            raf.writeByte(1);

                            raf.seek(raf.length());
                            long novaPosicao = raf.getFilePointer();
                            raf.writeByte(0);
                            raf.writeInt(dadosNovos.length);
                            raf.write(dadosNovos);

                            if (indice != null) {
                                // inserir() com uma chave ja existente atualiza a posicao no indice
                                indice.inserir(filmeAtualizado.getId(), novaPosicao);
                            }
                            return true;
                        }
                    }
                } else {
                    // registro ja deletado: so pula os bytes dele sem processar
                    raf.skipBytes(tamanhoAntigo);
                }
            }
        }
        return false; // id nao encontrado
    }

    // marca um filme como deletado (lapide = 1), sem remover fisicamente do arquivo
    public boolean deletarFilme(int id) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {
            raf.seek(4);
            
            while (raf.getFilePointer() < raf.length()) {
                long posicaoLapide = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    if (filme.getId() == id) {
                        raf.seek(posicaoLapide);
                        raf.writeByte(1); // marca a lapide como deletado

                        if (indice != null) {
                            indice.remover(id);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    // percorre o arquivo inteiro e imprime todos os filmes validos (nao deletados)
    public void listarTodos() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(4);
            int contador = 0;
            
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    System.out.println("[" + filme.getId() + "] " + filme.getMovieTitle());
                    contador++;
                }
            }
            System.out.println("\nTotal de filmes válidos: " + contador);
        }
    }
}