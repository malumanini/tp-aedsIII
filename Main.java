import java.time.LocalDate;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        String caminhoBinario = "filmes.dat";
        Scanner scanner = new Scanner(System.in);
        
        boolean sair = false;
        while (!sair) {
            System.out.println("\n=== MENU PRINCIPAL ===");
            System.out.println("1. Carregar CSV");
            System.out.println("2. CRUD Sequencial");
            System.out.println("3. Ordenacao Externa");
            System.out.println("4. Sair");
            System.out.print("> ");
            
            int opcao = scanner.nextInt();
            scanner.nextLine();
            
            if (opcao == 1) {
                System.out.println("Carregando CSV...");
                CsvLoader.carregarCSV("disney_movies_dataset.csv", caminhoBinario);
            } else if (opcao == 2) {
                menuCRUD(caminhoBinario, scanner);
            } else if (opcao == 3) {
                testeOrdenacao(caminhoBinario, scanner);
            } else if (opcao == 4) {
                System.out.println("Ate logo!");
                sair = true;
            } else {
                System.out.println("Opcao invalida!");
            }
        }
    }
    
    private static void menuCRUD(String caminhoBinario, Scanner scanner) throws Exception {
        FilmeDAO dao = new FilmeDAO(caminhoBinario);
        
        boolean voltar = false;
        while (!voltar) {
            limparTela();
            System.out.println("\n=== CRUD ===");
            System.out.println("1. Ler por ID");
            System.out.println("2. Criar novo");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Listar todos");
            System.out.println("6. Voltar");
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
                        if (dao.atualizarFilme(f)) {
                            System.out.println("Atualizado!");
                        }
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 4) {
                    System.out.print("ID para deletar: ");
                    int id = scanner.nextInt();
                    if (dao.deletarFilme(id)) {
                        System.out.println("Deletado!");
                    } else {
                        System.out.println("Nao encontrado");
                    }
                } else if (op == 5) {
                    dao.listarTodos();
                } else if (op == 6) {
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