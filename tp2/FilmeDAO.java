import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Classe responsavel pelas operacoes de CRUD no arquivo binario sequencial.
// A partir do TP2, o DAO tambem mantem os indices coerentes com o arquivo de dados:
//   - indiceBPlus:    Arvore B+ (chave = id)
//   - indiceHash:     Hashing Estendido (chave = id)
//   - indiceGeneros / indiceFranchise: Listas Invertidas (chave = termo textual)
// Toda insercao/atualizacao/remocao reflete em todos os indices que estiverem presentes
// (cada um pode ser null, caso o CRUD esteja rodando sem aquele indice em especifico).
public class FilmeDAO {
    private String caminhoArquivo;
    private BPlusTree indiceBPlus;
    private ExtendibleHash indiceHash;
    private InvertedList indiceGeneros;
    private InvertedList indiceFranchise;

    // construtor do TP1: sem nenhum indice, CRUD 100% sequencial
    public FilmeDAO(String caminhoArquivo) {
        this(caminhoArquivo, null, null, null, null);
    }

    // construtor so com a Arvore B+ (mantido por compatibilidade com o que ja existia)
    public FilmeDAO(String caminhoArquivo, BPlusTree indiceBPlus) {
        this(caminhoArquivo, indiceBPlus, null, null, null);
    }

    // construtor completo do TP2: qualquer indice pode ser null se ainda nao tiver sido construido
    public FilmeDAO(String caminhoArquivo, BPlusTree indiceBPlus, ExtendibleHash indiceHash,
                     InvertedList indiceGeneros, InvertedList indiceFranchise) {
        this.caminhoArquivo = caminhoArquivo;
        this.indiceBPlus = indiceBPlus;
        this.indiceHash = indiceHash;
        this.indiceGeneros = indiceGeneros;
        this.indiceFranchise = indiceFranchise;
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

    // le um registro na posicao informada (usado pelas duas buscas indexadas por id abaixo)
    private Filme lerNaPosicao(long posicao) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(posicao);
            byte lapide = raf.readByte();
            if (lapide == 1) return null; // registro foi deletado
            int tamanho = raf.readInt();
            byte[] dados = new byte[tamanho];
            raf.readFully(dados);
            return Filme.fromByteArray(dados);
        }
    }

    // busca um filme pelo id usando o indice em Arvore B+ (O(log n)). Sem indice, cai pro sequencial.
    public Filme lerPorIdIndexado(int id) throws IOException {
        if (indiceBPlus == null) return lerPorId(id);
        long posicao = indiceBPlus.buscar(id);
        if (posicao == -1) return null;
        return lerNaPosicao(posicao);
    }

    // busca um filme pelo id usando o indice de Hashing Estendido. Sem indice, cai pro sequencial.
    public Filme lerPorIdViaHash(int id) throws IOException {
        if (indiceHash == null) return lerPorId(id);
        long posicao = indiceHash.buscar(id);
        if (posicao == -1) return null;
        return lerNaPosicao(posicao);
    }

    // resolve uma lista de ids (vinda de uma lista invertida) para os filmes correspondentes,
    // usando o indice mais rapido disponivel (B+ > Hash > sequencial, nessa ordem de preferencia)
    private List<Filme> resolverIds(List<Integer> ids) throws IOException {
        List<Filme> resultado = new ArrayList<>();
        for (int id : ids) {
            Filme f;
            if (indiceBPlus != null) f = lerPorIdIndexado(id);
            else if (indiceHash != null) f = lerPorIdViaHash(id);
            else f = lerPorId(id);
            if (f != null) resultado.add(f);
        }
        return resultado;
    }

    // busca todos os filmes de um genero, usando a Lista Invertida de generos
    public List<Filme> buscarPorGenero(String genero) throws IOException {
        if (indiceGeneros == null) return new ArrayList<>();
        return resolverIds(indiceGeneros.buscar(genero));
    }

    // busca todos os filmes de uma franquia, usando a Lista Invertida de franchise
    public List<Filme> buscarPorFranchise(String franchise) throws IOException {
        if (indiceFranchise == null) return new ArrayList<>();
        return resolverIds(indiceFranchise.buscar(franchise));
    }

    // combina as duas Listas Invertidas numa mesma pesquisa (intersecao: genero E franchise)
    public List<Filme> buscarPorGeneroEFranchise(String genero, String franchise) throws IOException {
        if (indiceGeneros == null || indiceFranchise == null) return new ArrayList<>();
        List<Integer> idsGenero = indiceGeneros.buscar(genero);
        List<Integer> idsFranchise = indiceFranchise.buscar(franchise);
        List<Integer> idsCombinados = InvertedList.intersecao(idsGenero, idsFranchise);
        return resolverIds(idsCombinados);
    }

    // separa a string de generos (separados por "|") em termos individuais, prontos pra indexar
    private List<String> generosComoTermos(Filme filme) {
        return Arrays.asList(filme.getGenres().split("\\|"));
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
            long posicaoRegistro = raf.getFilePointer(); // posicao que vai para os indices
            raf.writeByte(0); // lapide 0 = valido
            raf.writeInt(dados.length);
            raf.write(dados);

            // atualiza o cabecalho com o novo ultimo id usado
            raf.seek(0);
            raf.writeInt(novoId);

            // mantem todos os indices presentes coerentes com o arquivo de dados
            if (indiceBPlus != null) {
                indiceBPlus.inserir(novoId, posicaoRegistro);
                System.out.println("[indice] Arvore B+ atualizada (insercao)");
            }
            if (indiceHash != null) {
                indiceHash.inserir(novoId, posicaoRegistro);
                System.out.println("[indice] Hashing Estendido atualizado (insercao)");
            }
            if (indiceGeneros != null) {
                indiceGeneros.inserir(novoId, generosComoTermos(filme));
                System.out.println("[indice] Lista Invertida de generos atualizada (insercao)");
            }
            if (indiceFranchise != null) {
                indiceFranchise.inserir(novoId, Collections.singletonList(filme.getFranchise()));
                System.out.println("[indice] Lista Invertida de franchise atualizada (insercao)");
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

                        // as Listas Invertidas dependem do CONTEUDO (genero/franchise), nao da posicao,
                        // entao precisam ser atualizadas nos dois casos abaixo caso o filme tenha mudado
                        // de genero/franchise: remove os termos antigos e insere os novos
                        if (indiceGeneros != null) {
                            indiceGeneros.remover(filmeAtualizado.getId(), generosComoTermos(filmeAntigo));
                            indiceGeneros.inserir(filmeAtualizado.getId(), generosComoTermos(filmeAtualizado));
                            System.out.println("[indice] Lista Invertida de generos atualizada (atualizacao)");
                        }
                        if (indiceFranchise != null) {
                            indiceFranchise.remover(filmeAtualizado.getId(), Collections.singletonList(filmeAntigo.getFranchise()));
                            indiceFranchise.inserir(filmeAtualizado.getId(), Collections.singletonList(filmeAtualizado.getFranchise()));
                            System.out.println("[indice] Lista Invertida de franchise atualizada (atualizacao)");
                        }

                        // caso 1: o registro novo ocupa o mesmo espaco do antigo -> sobrescreve no lugar
                        // a posicao do registro nao muda, entao Arvore B+ e Hash nao precisam ser tocados
                        if (dadosNovos.length == tamanhoAntigo) {
                            raf.seek(posicaoLapide + 5); // pula lapide (1 byte) + tamanho (4 bytes)
                            raf.write(dadosNovos);
                            return true;
                        }
                        // caso 2: o tamanho mudou -> marca o registro antigo como deletado
                        // e escreve o novo no final do arquivo (a posicao muda -> Arvore B+ e Hash precisam ser atualizados)
                        else {
                            raf.seek(posicaoLapide);
                            raf.writeByte(1);

                            raf.seek(raf.length());
                            long novaPosicao = raf.getFilePointer();
                            raf.writeByte(0);
                            raf.writeInt(dadosNovos.length);
                            raf.write(dadosNovos);

                            if (indiceBPlus != null) {
                                // inserir() com uma chave ja existente atualiza a posicao no indice
                                indiceBPlus.inserir(filmeAtualizado.getId(), novaPosicao);
                                System.out.println("[indice] Arvore B+ atualizada (atualizacao - posicao mudou)");
                            }
                            if (indiceHash != null) {
                                indiceHash.inserir(filmeAtualizado.getId(), novaPosicao);
                                System.out.println("[indice] Hashing Estendido atualizado (atualizacao - posicao mudou)");
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

                        if (indiceBPlus != null) {
                            indiceBPlus.remover(id);
                            System.out.println("[indice] Arvore B+ atualizada (remocao)");
                        }
                        if (indiceHash != null) {
                            indiceHash.remover(id);
                            System.out.println("[indice] Hashing Estendido atualizado (remocao)");
                        }
                        if (indiceGeneros != null) {
                            indiceGeneros.remover(id, generosComoTermos(filme));
                            System.out.println("[indice] Lista Invertida de generos atualizada (remocao)");
                        }
                        if (indiceFranchise != null) {
                            indiceFranchise.remover(id, Collections.singletonList(filme.getFranchise()));
                            System.out.println("[indice] Lista Invertida de franchise atualizada (remocao)");
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