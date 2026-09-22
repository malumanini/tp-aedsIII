import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Classe responsavel por ler o CSV e converter os dados para o arquivo binario
public class CsvLoader {

    public static void carregarCSV(String caminhoCSV, String caminhoBinario) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(caminhoBinario, "rw");
        
        // reserva o espaco do cabecalho (o ultimo id sera escrito no final, depois de saber quantos filmes foram carregados)
        raf.writeInt(0);
        
        int proximoId = 1; // ids sao gerados sequencialmente a partir do 1
        
        BufferedReader leitor = new BufferedReader(new FileReader(caminhoCSV));
        
        // pula a primeira linha, que e o cabecalho do CSV (nomes das colunas)
        String primeiraLinha = leitor.readLine();
        System.out.println("Header lido");
        
        String linha;
        int contador = 0;
        
        // le o CSV linha por linha ate o fim do arquivo
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
                // se uma linha estiver corrompida ou incompleta, ignora e segue para a proxima
                System.out.println("Erro na linha " + (contador + 1) + ": " + e.getMessage());
            }
        }
        
        // volta ao inicio do arquivo para gravar o ultimo id usado no cabecalho
        raf.seek(0);
        raf.writeInt(proximoId - 1);
        
        leitor.close();
        raf.close();
        
        System.out.println("✓ Carregamento completo! " + contador + " filmes salvos.");
    }
    
    // separa uma linha do CSV em campos, respeitando virgulas que estao dentro de aspas
    // (ex: "Filme, o Retorno" nao deve ser quebrado em dois campos por causa da virgula)
    private static String[] separarCSV(String linha) {
        java.util.List<String> campos = new java.util.ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroDeAspas = false;
        
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (c == ',' && !dentroDeAspas) {
                // virgula fora de aspas: fecha o campo atual e comeca outro
                campos.add(campo.toString().trim());
                campo = new StringBuilder();
            } else {
                campo.append(c);
            }
        }
        
        campos.add(campo.toString().trim()); // adiciona o ultimo campo da linha
        return campos.toArray(new String[0]);
    }
    
    // monta um objeto Filme a partir dos campos de uma linha do CSV
    private static Filme parseLinhaCSV(String linha, int id) throws Exception {
        String[] campos = separarCSV(linha);
        
        if (campos.length < 13) {
            throw new Exception("CSV com poucos campos: " + campos.length);
        }
        
        // pega apenas as colunas do CSV que interessam para o Filme (os indices seguem a ordem do CSV original)
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
                runtimeMinutes = 0; // se nao for um numero valido, assume 0 em vez de quebrar o carregamento
            }
        }
        
        LocalDate releaseDate = parseData(releaseDateStr);
        
        return new Filme(id, movieId, movieTitle, franchise, releaseDate, 
                        genre, runtimeMinutes, rating, country);
    }
    
    // converte a data do CSV para LocalDate, tentando alguns formatos diferentes
    // (o dataset tinha datas completas e tambem so o ano em algumas linhas)
    private static LocalDate parseData(String dataStr) throws Exception {
        if (dataStr == null || dataStr.isEmpty()) {
            return LocalDate.now();
        }
        
        try {
            // tenta o formato completo primeiro (ex: 2020-05-14)
            return LocalDate.parse(dataStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e1) {
            try {
                // se falhar, tenta interpretar como apenas o ano (ex: 2020)
                int ano = Integer.parseInt(dataStr);
                return LocalDate.of(ano, 1, 1);
            } catch (Exception e2) {
                // se nada funcionar, usa a data atual como valor padrao
                return LocalDate.now();
            }
        }
    }
    
    // remove aspas dos campos do CSV e tira espacos extras
    private static String removerAspas(String s) {
        if (s == null) return "";
        return s.replace("\"", "").trim();
    }
    
    // escreve um filme no final do arquivo binario, no formato lapide + tamanho + dados
    private static void salvarFilmeNoBinario(RandomAccessFile raf, Filme filme) throws IOException {
        byte[] dados = filme.toByteArray();
        
        raf.seek(raf.length());
        raf.writeByte(0); // lapide 0 = registro valido
        raf.writeInt(dados.length);
        raf.write(dados);
    }
}