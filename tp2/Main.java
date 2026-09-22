import java.io.File;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

// Classe principal com o menu interativo do sistema, rodado pelo terminal
public class Main {
    private static final String CAMINHO_INDICE_BPLUS = "indice_bplus.dat";
    private static final int ORDEM_BPLUS = 4; // parametrizavel; testado tambem com ordens maiores

    private static final String CAMINHO_INDICE_HASH = "indice_hash.dat";
    // capacidade do bucket = 5% do tamanho inicial da base (1000 filmes -> 50), conforme o enunciado
    private static final int CAPACIDADE_BUCKET_HASH = 50;

    private static final String CAMINHO_LISTA_GENEROS = "lista_generos.txt";
    private static final String CAMINHO_LISTA_FRANCHISE = "lista_franchise.txt";

    public static void main(String[] args) throws Exception {
        String caminhoBinario = "filmes.dat";
        Scanner scanner = new Scanner(System.in);

        BPlusTree indiceBPlus = null;
        ExtendibleHash indiceHash = null;
        InvertedList indiceGeneros = null;
        InvertedList indiceFranchise = null;

        boolean sair = false;
        while (!sair) {
            System.out.println("\n=== MENU PRINCIPAL ===");
            System.out.println("1. Carregar CSV");
            System.out.println("2. CRUD Sequencial (sem indice)");
            System.out.println("3. Ordenacao Externa");
            System.out.println("4. Construir/Reconstruir indice Arvore B+");
            System.out.println("5. Construir/Reconstruir indice Hashing Estendido");
            System.out.println("6. Construir/Reconstruir Listas Invertidas (generos e franchise)");
            System.out.println("7. CRUD com indices");
            System.out.println("8. Sair");
            System.out.print("> ");

            int opcao = scanner.nextInt();
            scanner.nextLine();

            if (opcao == 1) {
                System.out.println("Carregando CSV...");
                CsvLoader.carregarCSV("disney_movies_dataset.csv", caminhoBinario);
                // qualquer indice anterior ficou desatualizado em relacao aos novos dados
                indiceBPlus = null;
                indiceHash = null;
                indiceGeneros = null;
                indiceFranchise = null;
            } else if (opcao == 2) {
                menuCRUD(caminhoBinario, scanner, null, null, null, null);
            } else if (opcao == 3) {
                testeOrdenacao(caminhoBinario, scanner);
            } else if (opcao == 4) {
                indiceBPlus = construirIndiceBPlus(caminhoBinario, CAMINHO_INDICE_BPLUS, ORDEM_BPLUS);
                System.out.println("Indice Arvore B+ construido/reconstruido com sucesso.");
            } else if (opcao == 5) {
                indiceHash = construirIndiceHash(caminhoBinario, CAMINHO_INDICE_HASH, CAPACIDADE_BUCKET_HASH);
                System.out.println("Indice Hashing Estendido construido/reconstruido com sucesso.");
            } else if (opcao == 6) {
                new File(CAMINHO_LISTA_GENEROS).delete();
                new File(CAMINHO_LISTA_FRANCHISE).delete();
                indiceGeneros = new InvertedList(CAMINHO_LISTA_GENEROS);
                indiceFranchise = new InvertedList(CAMINHO_LISTA_FRANCHISE);
                construirListasInvertidas(caminhoBinario, indiceGeneros, indiceFranchise);
                System.out.println("Listas Invertidas construidas/reconstruidas com sucesso.");
            } else if (opcao == 7) {
                menuCRUD(caminhoBinario, scanner, indiceBPlus, indiceHash, indiceGeneros, indiceFranchise);
            } else if (opcao == 8) {
                System.out.println("Ate logo!");
                sair = true;
            } else {
                System.out.println("Opcao invalida!");
            }
        }

        if (indiceBPlus != null) indiceBPlus.close();
        if (indiceHash != null) indiceHash.close();
    }

    // Varre o arquivo de dados sequencialmente e insere cada registro valido na Arvore B+.
    private static BPlusTree construirIndiceBPlus(String caminhoBinario, String caminhoIndice, int ordem) throws Exception {
        File antigo = new File(caminhoIndice);
        if (antigo.exists()) antigo.delete();

        BPlusTree arvore = new BPlusTree(caminhoIndice, ordem);

        try (RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "r")) {
            raf.seek(4);
            while (raf.getFilePointer() < raf.length()) {
                long posicaoRegistro = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);

                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    arvore.inserir(filme.getId(), posicaoRegistro);
                }
            }
        }
        return arvore;
    }

    // Varre o arquivo de dados sequencialmente e insere cada registro valido no Hashing Estendido.
    private static ExtendibleHash construirIndiceHash(String caminhoBinario, String caminhoIndice, int capacidadeBucket) throws Exception {
        File antigo = new File(caminhoIndice);
        if (antigo.exists()) antigo.delete();

        ExtendibleHash hash = new ExtendibleHash(caminhoIndice, capacidadeBucket);

        try (RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "r")) {
            raf.seek(4);
            while (raf.getFilePointer() < raf.length()) {
                long posicaoRegistro = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);

                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    hash.inserir(filme.getId(), posicaoRegistro);
                }
            }
        }
        return hash;
    }

    // Varre o arquivo de dados sequencialmente e popula as duas Listas Invertidas (generos e franchise).
    private static void construirListasInvertidas(String caminhoBinario, InvertedList generos, InvertedList franchise) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "r")) {
            raf.seek(4);
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);

                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    generos.inserir(filme.getId(), java.util.Arrays.asList(filme.getGenres().split("\\|")));
                    franchise.inserir(filme.getId(), java.util.Collections.singletonList(filme.getFranchise()));
                }
            }
        }
    }

    // menu com as operacoes de CRUD sobre o arquivo binario.
    // qualquer indice pode ser null (nao construido ainda); o DAO so usa os que estiverem presentes
    private static void menuCRUD(String caminhoBinario, Scanner scanner, BPlusTree indiceBPlus,
                                  ExtendibleHash indiceHash, InvertedList indiceGeneros, InvertedList indiceFranchise) throws Exception {
        FilmeDAO dao = new FilmeDAO(caminhoBinario, indiceBPlus, indiceHash, indiceGeneros, indiceFranchise);
        boolean temAlgumIndice = indiceBPlus != null || indiceHash != null || indiceGeneros != null || indiceFranchise != null;

        limparTela();

        boolean voltar = false;
        while (!voltar) {
            System.out.println("\n=== CRUD" + (temAlgumIndice ? " (com indices)" : "") + " ===");
            System.out.println("1. Ler por ID (sequencial)");
            System.out.println("2. Criar novo");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Listar todos");
            System.out.println("6. Ler por ID via Arvore B+" + (indiceBPlus == null ? " (indisponivel)" : ""));
            System.out.println("7. Ler por ID via Hashing Estendido" + (indiceHash == null ? " (indisponivel)" : ""));
            System.out.println("8. Buscar por genero (Lista Invertida)" + (indiceGeneros == null ? " (indisponivel)" : ""));
            System.out.println("9. Buscar por franchise (Lista Invertida)" + (indiceFranchise == null ? " (indisponivel)" : ""));
            System.out.println("10. Buscar por genero E franchise (combina as duas Listas Invertidas)" + ((indiceGeneros == null || indiceFranchise == null) ? " (indisponivel)" : ""));
            System.out.println("11. Voltar");
            System.out.print("> ");

            int op = scanner.nextInt();
            scanner.nextLine();

            try {
                if (op == 1) {
                    System.out.print("ID: ");
                    int id = scanner.nextInt();
                    Filme f = dao.lerPorId(id);
                    System.out.println(f != null ? f : "Nao encontrado");
                } else if (op == 2) {
                    System.out.print("Titulo: ");
                    String titulo = scanner.nextLine();
                    System.out.print("Franchise: ");
                    String franchise = scanner.nextLine();
                    System.out.print("Genero: ");
                    String genero = scanner.nextLine();
                    System.out.print("Runtime (min): ");
                    int runtime = scanner.nextInt();
                    scanner.nextLine();
                    System.out.print("Rating: ");
                    String rating = scanner.nextLine();
                    System.out.print("Pais: ");
                    String pais = scanner.nextLine();

                    Filme novo = new Filme(0, "AUTO", titulo, franchise, LocalDate.now(), genero, runtime, rating, pais);
                    int novoId = dao.criarFilme(novo);
                    System.out.println("Criado com ID: " + novoId);
                } else if (op == 3) {
                    System.out.print("ID para atualizar: ");
                    int id = scanner.nextInt();
                    scanner.nextLine();
                    Filme f = dao.lerPorId(id);
                    if (f != null) {
                        System.out.print("Novo titulo: ");
                        String novoTitulo = scanner.nextLine();
                        f.setMovieTitle(novoTitulo);
                        if (dao.atualizarFilme(f)) System.out.println("Atualizado!");
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 4) {
                    System.out.print("ID para deletar: ");
                    int id = scanner.nextInt();
                    System.out.println(dao.deletarFilme(id) ? "Deletado!" : "Nao encontrado");
                } else if (op == 5) {
                    dao.listarTodos();
                } else if (op == 6) {
                    if (indiceBPlus == null) {
                        System.out.println("Indice Arvore B+ nao construido (opcao 4 do menu principal).");
                    } else {
                        System.out.print("ID: ");
                        int id = scanner.nextInt();
                        long inicio = System.nanoTime();
                        Filme f = dao.lerPorIdIndexado(id);
                        long duracaoNs = System.nanoTime() - inicio;
                        System.out.println(f != null ? f : "Nao encontrado");
                        System.out.println("(busca via Arvore B+ - " + duracaoNs + "ns)");
                    }
                } else if (op == 7) {
                    if (indiceHash == null) {
                        System.out.println("Indice Hashing Estendido nao construido (opcao 5 do menu principal).");
                    } else {
                        System.out.print("ID: ");
                        int id = scanner.nextInt();
                        long inicio = System.nanoTime();
                        Filme f = dao.lerPorIdViaHash(id);
                        long duracaoNs = System.nanoTime() - inicio;
                        System.out.println(f != null ? f : "Nao encontrado");
                        System.out.println("(busca via Hashing Estendido - " + duracaoNs + "ns)");
                    }
                } else if (op == 8) {
                    if (indiceGeneros == null) {
                        System.out.println("Lista Invertida de generos nao construida (opcao 6 do menu principal).");
                    } else {
                        System.out.print("Genero: ");
                        String genero = scanner.nextLine();
                        List<Filme> resultado = dao.buscarPorGenero(genero);
                        System.out.println("(busca via Lista Invertida de generos - " + resultado.size() + " resultado(s))");
                        for (Filme f : resultado) System.out.println(f);
                    }
                } else if (op == 9) {
                    if (indiceFranchise == null) {
                        System.out.println("Lista Invertida de franchise nao construida (opcao 6 do menu principal).");
                    } else {
                        System.out.print("Franchise: ");
                        String franchise = scanner.nextLine();
                        List<Filme> resultado = dao.buscarPorFranchise(franchise);
                        System.out.println("(busca via Lista Invertida de franchise - " + resultado.size() + " resultado(s))");
                        for (Filme f : resultado) System.out.println(f);
                    }
                } else if (op == 10) {
                    if (indiceGeneros == null || indiceFranchise == null) {
                        System.out.println("Ambas as Listas Invertidas precisam estar construidas (opcao 6 do menu principal).");
                    } else {
                        System.out.print("Genero: ");
                        String genero = scanner.nextLine();
                        System.out.print("Franchise: ");
                        String franchise = scanner.nextLine();
                        List<Filme> resultado = dao.buscarPorGeneroEFranchise(genero, franchise);
                        System.out.println("(busca combinando as duas Listas Invertidas - " + resultado.size() + " resultado(s))");
                        for (Filme f : resultado) System.out.println(f);
                    }
                } else if (op == 11) {
                    voltar = true;
                } else {
                    System.out.println("Opcao invalida");
                }
            } catch (Exception e) {
                System.out.println("Erro: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void testeOrdenacao(String caminhoBinario, Scanner scanner) throws Exception {
        System.out.println("\n=== ORDENACAO EXTERNA ===");
        System.out.print("Max registros por lote (padrao 100): ");
        int max = scanner.nextInt();
        if (max <= 0) max = 100;

        System.out.print("Num caminhos (2 ou 3, padrao 2): ");
        int ways = scanner.nextInt();
        if (ways < 2 || ways > 3) ways = 2;

        String caminhoTemp = "filmes_ordenado.dat";
        ExternalSort sorter = new ExternalSort(caminhoBinario, caminhoTemp, max, ways);

        long inicio = System.currentTimeMillis();
        sorter.ordenar();
        long duracao = System.currentTimeMillis() - inicio;

        System.out.println("\nTempo total: " + duracao + "ms");
        System.out.println("Arquivo ordenado criado: " + caminhoTemp);
    }

    private static void limparTela() {
        try {
            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
        } catch (Exception e) {
        }
    }
}