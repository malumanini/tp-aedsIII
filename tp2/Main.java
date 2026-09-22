import java.io.File;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.util.Scanner;

// Classe principal com o menu interativo do sistema, rodado pelo terminal
public class Main {
    private static final String CAMINHO_INDICE_BPLUS = "indice_btree.dat";
    private static final int ORDEM_BPLUS = 4; // parametrizavel; testado tambem com ordens maiores

    public static void main(String[] args) throws Exception {
        String caminhoBinario = "filmes.dat";
        Scanner scanner = new Scanner(System.in);

        BPlusTree indice = null; // so existe depois que o usuario constroi (opcao 5)

        boolean sair = false;
        while (!sair) {
            System.out.println("\n=== MENU PRINCIPAL ===");
            System.out.println("1. Carregar CSV");
            System.out.println("2. CRUD Sequencial (sem indice)");
            System.out.println("3. Ordenacao Externa");
            System.out.println("4. Construir/Reconstruir indice (Arvore B+)");
            System.out.println("5. CRUD com indice (Arvore B+)");
            System.out.println("6. Sair");
            System.out.print("> ");
            
            int opcao = scanner.nextInt();
            scanner.nextLine(); // consome a quebra de linha deixada pelo nextInt
            
            if (opcao == 1) {
                System.out.println("Carregando CSV...");
                CsvLoader.carregarCSV("disney_movies_dataset.csv", caminhoBinario);
                indice = null; // o indice antigo (se existia) ficou desatualizado
            } else if (opcao == 2) {
                menuCRUD(caminhoBinario, scanner, null);
            } else if (opcao == 3) {
                testeOrdenacao(caminhoBinario, scanner);
            } else if (opcao == 4) {
                indice = construirIndice(caminhoBinario, CAMINHO_INDICE_BPLUS, ORDEM_BPLUS);
                System.out.println("Indice construido/reconstruido com sucesso.");
            } else if (opcao == 5) {
                if (indice == null) {
                    System.out.println("O indice ainda nao foi construido. Rode a opcao 4 primeiro.");
                } else {
                    menuCRUD(caminhoBinario, scanner, indice);
                }
            } else if (opcao == 6) {
                System.out.println("Ate logo!");
                sair = true;
            } else {
                System.out.println("Opcao invalida!");
            }
        }

        if (indice != null) {
            indice.close();
        }
    }

    // Varre o arquivo de dados sequencialmente do inicio ao fim e insere cada registro valido
    // no indice, construindo a Arvore B+ do zero. Usada apos carregar o CSV ou sempre que
    // o usuario quiser garantir que o indice esta coerente com filmes.dat.
    private static BPlusTree construirIndice(String caminhoBinario, String caminhoIndice, int ordem) throws Exception {
        // remove o indice antigo para garantir que estamos construindo do zero
        File antigo = new File(caminhoIndice);
        if (antigo.exists()) {
            antigo.delete();
        }

        BPlusTree arvore = new BPlusTree(caminhoIndice, ordem);

        try (RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "r")) {
            raf.seek(4); // pula o cabecalho do arquivo de dados

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
    
    // menu com as operacoes de CRUD sobre o arquivo binario.
    // se "indice" for null, roda como no TP1 (sequencial); se nao for, mantem o indice coerente
    // e habilita a busca indexada (opcao 7 do submenu)
    private static void menuCRUD(String caminhoBinario, Scanner scanner, BPlusTree indice) throws Exception {
        FilmeDAO dao = (indice == null) ? new FilmeDAO(caminhoBinario) : new FilmeDAO(caminhoBinario, indice);
        
        limparTela(); // limpa a tela so na entrada do CRUD, nao a cada operacao (senao apagaria os resultados)
        
        boolean voltar = false;
        while (!voltar) {
            System.out.println("\n=== CRUD" + (indice != null ? " (com indice B+)" : "") + " ===");
            System.out.println("1. Ler por ID (sequencial)");
            System.out.println("2. Criar novo");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Listar todos");
            if (indice != null) {
                System.out.println("6. Ler por ID (via indice B+)");
                System.out.println("7. Voltar");
            } else {
                System.out.println("6. Voltar");
            }
            System.out.print("> ");
            
            int op = scanner.nextInt();
            scanner.nextLine();
            
            try {
                if (op == 1) {
                    System.out.print("ID: ");
                    int id = scanner.nextInt();
                    Filme f = dao.lerPorId(id);
                    if (f != null) {
                        System.out.println(f);
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 2) {
                    // pega os dados do novo filme com o usuario; o id fica 0 porque quem gera o id de verdade e o DAO
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
                    System.out.println("Criado com ID: " + novoId + (indice != null ? " (indice atualizado)" : ""));
                } else if (op == 3) {
                    // le o filme atual, altera o titulo e manda de volta pro DAO atualizar
                    System.out.print("ID para atualizar: ");
                    int id = scanner.nextInt();
                    scanner.nextLine();
                    Filme f = dao.lerPorId(id);
                    if (f != null) {
                        System.out.print("Novo titulo: ");
                        String novoTitulo = scanner.nextLine();
                        f.setMovieTitle(novoTitulo);
                        if (dao.atualizarFilme(f)) {
                            System.out.println("Atualizado!" + (indice != null ? " (indice atualizado)" : ""));
                        }
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 4) {
                    System.out.print("ID para deletar: ");
                    int id = scanner.nextInt();
                    if (dao.deletarFilme(id)) {
                        System.out.println("Deletado!" + (indice != null ? " (removido do indice)" : ""));
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 5) {
                    dao.listarTodos();
                } else if (op == 6 && indice != null) {
                    System.out.print("ID: ");
                    int id = scanner.nextInt();
                    long inicio = System.nanoTime();
                    Filme f = dao.lerPorIdIndexado(id);
                    long duracaoNs = System.nanoTime() - inicio;
                    if (f != null) {
                        System.out.println(f);
                        System.out.println("(busca via indice B+ - " + duracaoNs + "ns)");
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if ((op == 6 && indice == null) || (op == 7 && indice != null)) {
                    voltar = true;
                } else {
                    System.out.println("Opcao invalida");
                }
            } catch (Exception e) {
                // captura qualquer erro de uma operacao especifica sem derrubar o menu inteiro
                System.out.println("Erro: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // pede os parametros da ordenacao externa e roda, medindo o tempo total gasto
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

    // limpa o terminal (so funciona no Windows, por causa do comando "cls")
    private static void limparTela() {
        try {
            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
        } catch (Exception e) {

        }
    }
}