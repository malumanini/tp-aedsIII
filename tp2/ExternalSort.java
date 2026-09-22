import java.io.*;
import java.util.*;

// Implementa a ordenacao externa por intercalacao balanceada (external merge sort)
public class ExternalSort {
    private String origem;
    private String destino;
    private int maxReg; // quantidade maxima de registros que cabem em memoria por vez
    private int ways;   // quantidade de arquivos intercalados por vez em cada rodada
    private List<File> arquivosTemp = new ArrayList<>();

    public ExternalSort(String origem, String destino, int maxReg, int ways) {
        this.origem = origem;
        this.destino = destino;
        this.maxReg = maxReg;
        this.ways = ways;
    }

    public void ordenar() throws IOException {
        System.out.println("Iniciando ordenacao externa...");
        System.out.println("Origem: " + origem);
        System.out.println("Destino: " + destino);
        System.out.println("Max registros por bloco: " + maxReg);
        System.out.println("Caminhos (ways) por intercalacao: " + ways);

        // fase 1: divide o arquivo original em varios blocos menores, cada um ja ordenado
        List<File> runs = criarRunsOrdenados();
        System.out.println("Fase 1 (distribuicao) concluida: " + runs.size() + " blocos criados");

        // fase 2: intercala os blocos em rodadas, ate sobrar apenas 1 arquivo totalmente ordenado
        int rodada = 1;
        while (runs.size() > 1) {
            System.out.println("Rodada de intercalacao " + rodada + ": " + runs.size() + " runs");
            runs = intercalarRodada(runs);
            rodada++;
        }

        // o unico arquivo que sobrou e o resultado final ordenado
        escreverArquivoFinal(runs.get(0));

        // limpa os arquivos temporarios que ainda restaram
        for (File f : arquivosTemp) {
            if (f.exists()) f.delete();
        }

        System.out.println("Ordenacao externa concluida!");
    }

    // le o arquivo original e quebra em blocos (runs) de ate maxReg registros, cada bloco ja ordenado por id
    private List<File> criarRunsOrdenados() throws IOException {
        List<File> runs = new ArrayList<>();
        List<Filme> buffer = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(origem, "r")) {
            raf.seek(4); // pula o header (ultimo id)

            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);

                // registros deletados (lapide != 0) nao entram no buffer, entao somem do arquivo ordenado
                if (lapide == 0) {
                    try {
                        buffer.add(Filme.fromByteArray(dados));
                    } catch (Exception e) {
                        // ignora registro corrompido
                    }
                }

                // quando o buffer enche, ordena e grava esse bloco em um arquivo temporario
                if (buffer.size() >= maxReg) {
                    runs.add(escreverRun(buffer));
                    buffer.clear();
                }
            }
        }

        // grava o que sobrou no buffer, caso nao tenha enchido um bloco inteiro
        if (!buffer.isEmpty()) {
            runs.add(escreverRun(buffer));
            buffer.clear();
        }

        return runs;
    }

    // ordena uma lista de filmes por id (em memoria) e grava num arquivo temporario
    private File escreverRun(List<Filme> filmes) throws IOException {
        filmes.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

        File temp = File.createTempFile("run_", ".tmp");
        temp.deleteOnExit();
        arquivosTemp.add(temp);

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temp)))) {
            for (Filme f : filmes) {
                byte[] dados = f.toByteArray();
                dos.writeInt(dados.length);
                dos.write(dados);
            }
        }

        return temp;
    }

    // faz uma rodada de intercalacao: agrupa os runs atuais de "ways" em "ways" e intercala cada grupo
    private List<File> intercalarRodada(List<File> runsAtuais) throws IOException {
        List<File> novosRuns = new ArrayList<>();

        for (int i = 0; i < runsAtuais.size(); i += ways) {
            int fim = Math.min(i + ways, runsAtuais.size());
            List<File> grupo = runsAtuais.subList(i, fim);
            novosRuns.add(intercalarGrupo(grupo));
        }

        // os runs dessa rodada ja foram intercalados em novos arquivos, entao podem ser apagados
        for (File f : runsAtuais) {
            f.delete();
            arquivosTemp.remove(f);
        }

        return novosRuns;
    }

    // intercala um grupo de arquivos ja ordenados em um unico arquivo, usando uma fila de prioridade
    private File intercalarGrupo(List<File> grupo) throws IOException {
        List<RunReader> leitores = new ArrayList<>();
        for (File f : grupo) {
            leitores.add(new RunReader(f));
        }

        File temp = File.createTempFile("merge_", ".tmp");
        temp.deleteOnExit();
        arquivosTemp.add(temp);

        // a fila de prioridade sempre mantem no topo o menor id entre os filmes atualmente "na mao" de cada leitor
        PriorityQueue<HeapNode> heap = new PriorityQueue<>(
                Comparator.comparingInt(n -> n.filme.getId()));

        // coloca o primeiro filme de cada arquivo na fila, para comecar a comparacao
        for (RunReader r : leitores) {
            Filme f = r.proximo();
            if (f != null) {
                heap.add(new HeapNode(f, r));
            }
        }

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temp)))) {

            // a cada passo, retira o menor elemento da fila e escreve no arquivo de saida;
            // depois busca o proximo filme do mesmo leitor e coloca de volta na fila
            while (!heap.isEmpty()) {
                HeapNode menor = heap.poll();
                byte[] dados = menor.filme.toByteArray();
                dos.writeInt(dados.length);
                dos.write(dados);

                Filme proximo = menor.leitor.proximo();
                if (proximo != null) {
                    heap.add(new HeapNode(proximo, menor.leitor));
                }
            }
        }

        for (RunReader r : leitores) {
            r.fechar();
        }

        return temp;
    }

    // le o run final (ja totalmente ordenado) e regrava no formato do arquivo binario do sistema
    // (com cabecalho e lapide), sobrescrevendo o arquivo de destino
    private void escreverArquivoFinal(File runFinal) throws IOException {
        List<Filme> filmes = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(runFinal)))) {
            while (true) {
                int tamanho;
                try {
                    tamanho = dis.readInt();
                } catch (EOFException e) {
                    break; // chegou ao fim do arquivo
                }
                byte[] dados = new byte[tamanho];
                dis.readFully(dados);
                filmes.add(Filme.fromByteArray(dados));
            }
        } catch (Exception e) {
            throw new IOException("Erro ao ler run final", e);
        }

        try (RandomAccessFile raf = new RandomAccessFile(destino, "rw")) {
            raf.setLength(0); // limpa o conteudo antigo do arquivo de destino
            raf.writeInt(filmes.size()); // cabecalho: quantidade de filmes no arquivo ordenado

            for (Filme f : filmes) {
                byte[] dados = f.toByteArray();
                raf.writeByte(0); // lapide 0 = valido (os deletados ja ficaram de fora la na fase 1)
                raf.writeInt(dados.length);
                raf.write(dados);
            }
        }
    }

    // le um arquivo de run (bloco ordenado) sequencialmente, um filme por vez
    private static class RunReader {
        private DataInputStream dis;

        RunReader(File arquivo) throws IOException {
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(arquivo)));
        }

        // retorna o proximo filme do arquivo, ou null se chegou ao fim
        Filme proximo() throws IOException {
            int tamanho;
            try {
                tamanho = dis.readInt();
            } catch (EOFException e) {
                return null;
            }
            byte[] dados = new byte[tamanho];
            dis.readFully(dados);
            try {
                return Filme.fromByteArray(dados);
            } catch (Exception e) {
                return null;
            }
        }

        void fechar() throws IOException {
            dis.close();
        }
    }

    // representa um elemento dentro da fila de prioridade: o filme atual de um leitor,
    // junto com o proprio leitor (para saber de onde buscar o proximo filme depois)
    private static class HeapNode {
        Filme filme;
        RunReader leitor;

        HeapNode(Filme filme, RunReader leitor) {
            this.filme = filme;
            this.leitor = leitor;
        }
    }
}