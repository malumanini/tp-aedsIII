import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvertedList {
    private String caminhoArquivo;
    private Map<String, List<Integer>> indice;

    public InvertedList(String caminhoArquivo) throws IOException {
        this.caminhoArquivo = caminhoArquivo;
        this.indice = new LinkedHashMap<>();
        carregar();
    }

    private void carregar() throws IOException {
        File f = new File(caminhoArquivo);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                if (linha.isBlank()) continue;
                int sep = linha.indexOf('=');
                if (sep == -1) continue;

                String termo = linha.substring(0, sep);
                String restante = linha.substring(sep + 1);
                List<Integer> ids = new ArrayList<>();
                if (!restante.isBlank()) {
                    for (String s : restante.split(",")) {
                        ids.add(Integer.parseInt(s.trim()));
                    }
                }
                indice.put(termo, ids);
            }
        }
    }

    // reescreve o arquivo inteiro com o estado atual em memoria - simples e viavel dado o
    // tamanho da base (1000 filmes); evita a complexidade de um arquivo binario de tamanho variavel
    private void persistir() throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(caminhoArquivo))) {
            for (Map.Entry<String, List<Integer>> entrada : indice.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.append(entrada.getKey()).append('=');
                List<Integer> ids = entrada.getValue();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(ids.get(i));
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }

    // associa o id a cada termo da lista (ex: os generos de um filme). Mantem a coerencia
    // gravando o arquivo logo em seguida.
    public void inserir(int id, List<String> termos) throws IOException {
        for (String termoOriginal : termos) {
            String termo = termoOriginal.trim().toLowerCase();
            if (termo.isEmpty()) continue;

            indice.computeIfAbsent(termo, k -> new ArrayList<>());
            if (!indice.get(termo).contains(id)) {
                indice.get(termo).add(id);
            }
        }
        persistir();
    }

    // remove o id de todos os termos informados (usado em delete e em update, antes de reinserir)
    public void remover(int id, List<String> termos) throws IOException {
        for (String termoOriginal : termos) {
            String termo = termoOriginal.trim().toLowerCase();
            List<Integer> ids = indice.get(termo);
            if (ids != null) {
                ids.remove(Integer.valueOf(id));
                if (ids.isEmpty()) indice.remove(termo);
            }
        }
        persistir();
    }

    // busca simples: todos os ids associados a um termo
    public List<Integer> buscar(String termo) {
        List<Integer> ids = indice.get(termo.trim().toLowerCase());
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    // intersecao entre duas listas de ids - permite combinar esta lista invertida com outra
    // numa mesma pesquisa (ex: genero X E franchise Y), conforme pedido no enunciado
    public static List<Integer> intersecao(List<Integer> a, List<Integer> b) {
        List<Integer> resultado = new ArrayList<>();
        for (Integer id : a) {
            if (b.contains(id)) resultado.add(id);
        }
        return resultado;
    }
}