import java.io.*;

// Classe responsavel pelas operacoes de CRUD no arquivo binario sequencial
public class FilmeDAO {
    private String caminhoArquivo;
    
    public FilmeDAO(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
    }
    
    // busca um filme pelo id, percorrendo o arquivo sequencialmente do inicio ao fim
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
            raf.writeByte(0); // lapide 0 = valido
            raf.writeInt(dados.length);
            raf.write(dados);
            
            // atualiza o cabecalho com o novo ultimo id usado
            raf.seek(0);
            raf.writeInt(novoId);
            
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
                        if (dadosNovos.length == tamanhoAntigo) {
                            raf.seek(posicaoLapide + 5); // pula lapide (1 byte) + tamanho (4 bytes)
                            raf.write(dadosNovos);
                            return true;
                        }
                        // caso 2: o tamanho mudou -> marca o registro antigo como deletado
                        // e escreve o novo no final do arquivo
                        else {
                            raf.seek(posicaoLapide);
                            raf.writeByte(1);
                            raf.seek(raf.length());
                            raf.writeByte(0);
                            raf.writeInt(dadosNovos.length);
                            raf.write(dadosNovos);
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