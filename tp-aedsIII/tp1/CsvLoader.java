import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CsvLoader {

    public static void carregarCSV(String caminhoCSV, String caminhoBinario) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "rw");
        
        raf.writeInt(0);
        
        int proximoId = 1;
        
        BufferedReader leitor = new BufferedReader(new FileReader(caminhoCSV));
        
        String primeiraLinha = leitor.readLine();
        System.out.println("Header lido");
        
        String linha;
        int contador = 0;
        
        while ((linha = leitor.readLine()) != null) {
            try {
                Filme filme = parseLinhaCSV(linha, proximoId);
                salvarFilmeNoBinario(raf, filme);
                
                proximoId++;
                contador++;
                
                if (contador % 100 == 0) {
                    System.out.println("  Carregados " + contador + " filmes...");
                }
            } catch (Exception e) {
                System.out.println("Erro na linha " + (contador + 1) + ": " + e.getMessage());
            }
        }
        
        raf.seek(0);
        raf.writeInt(proximoId - 1);
        
        leitor.close();
        raf.close();
        
        System.out.println("✓ Carregamento completo! " + contador + " filmes salvos.");
    }
    
    private static String[] separarCSV(String linha) {
        java.util.List<String> campos = new java.util.ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroDeAspas = false;
        
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (c == ',' && !dentroDeAspas) {
                campos.add(campo.toString().trim());
                campo = new StringBuilder();
            } else {
                campo.append(c);
            }
        }
        
        campos.add(campo.toString().trim());
        return campos.toArray(new String[0]);
    }
    
    private static Filme parseLinhaCSV(String linha, int id) throws Exception {
        String[] campos = separarCSV(linha);
        
        if (campos.length < 13) {
            throw new Exception("CSV com poucos campos: " + campos.length);
        }
        
        String movieId = removerAspas(campos[0]);
        String movieTitle = removerAspas(campos[1]);
        String franchise = removerAspas(campos[2]);
        String releaseDateStr = removerAspas(campos[4]);
        String genre = removerAspas(campos[7]);
        String runtimeStr = removerAspas(campos[9]);
        String rating = removerAspas(campos[10]);
        String country = removerAspas(campos[12]);
        
        if (movieId.isEmpty() || movieTitle.isEmpty()) {
            throw new Exception("movieId ou movieTitle vazios");
        }
        
        int runtimeMinutes = 0;
        if (!runtimeStr.isEmpty()) {
            try {
                runtimeMinutes = Integer.parseInt(runtimeStr);
            } catch (NumberFormatException e) {
                runtimeMinutes = 0;
            }
        }
        
        LocalDate releaseDate = parseData(releaseDateStr);
        
        return new Filme(id, movieId, movieTitle, franchise, releaseDate, 
                        genre, runtimeMinutes, rating, country);
    }
    
    private static LocalDate parseData(String dataStr) throws Exception {
        if (dataStr == null || dataStr.isEmpty()) {
            return LocalDate.now();
        }
        

        try {
            return LocalDate.parse(dataStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e1) {

            try {
                int ano = Integer.parseInt(dataStr);
                return LocalDate.of(ano, 1, 1);
            } catch (Exception e2) {
                return LocalDate.now();
            }
        }
    }
    
    private static String removerAspas(String s) {
        if (s == null) return "";
        return s.replace("\"", "").trim();
    }
    
    private static void salvarFilmeNoBinario(RandomAccessFile raf, Filme filme) throws IOException {
        byte[] dados = filme.toByteArray();
        
        raf.seek(raf.length());
        raf.writeByte(0);
        raf.writeInt(dados.length);
        raf.write(dados);
    }
}