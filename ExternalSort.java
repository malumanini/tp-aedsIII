import java.io.*;
import java.util.*;

public class ExternalSort {
    private String origem;
    private String destino;
    private int maxReg;
    private int ways;
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

        List<File> runs = criarRunsOrdenados();
        System.out.println("Fase 1 (distribuicao) concluida: " + runs.size() + " blocos criados");


        int rodada = 1;
        while (runs.size() > 1) {
            System.out.println("Rodada de intercalacao " + rodada + ": " + runs.size() + " runs");
            runs = intercalarRodada(runs);
            rodada++;
        }

        escreverArquivoFinal(runs.get(0));

        for (File f : arquivosTemp) {
            if (f.exists()) f.delete();
        }

        System.out.println("Ordenacao externa concluida!");
    }

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

                if (lapide == 0) {
                    try {
                        buffer.add(Filme.fromByteArray(dados));
                    } catch (Exception e) {
                        // ignora registro corrompido
                    }
                }

                if (buffer.size() >= maxReg) {
                    runs.add(escreverRun(buffer));
                    buffer.clear();
                }
            }
        }

        if (!buffer.isEmpty()) {
            runs.add(escreverRun(buffer));
            buffer.clear();
        }

        return runs;
    }

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

    private List<File> intercalarRodada(List<File> runsAtuais) throws IOException {
        List<File> novosRuns = new ArrayList<>();

        // agrupa os runs de "ways" em "ways" e funde cada grupo
        for (int i = 0; i < runsAtuais.size(); i += ways) {
            int fim = Math.min(i + ways, runsAtuais.size());
            List<File> grupo = runsAtuais.subList(i, fim);
            novosRuns.add(intercalarGrupo(grupo));
        }

        for (File f : runsAtuais) {
            f.delete();
            arquivosTemp.remove(f);
        }

        return novosRuns;
    }

    private File intercalarGrupo(List<File> grupo) throws IOException {
        List<RunReader> leitores = new ArrayList<>();
        for (File f : grupo) {
            leitores.add(new RunReader(f));
        }

        File temp = File.createTempFile("merge_", ".tmp");
        temp.deleteOnExit();
        arquivosTemp.add(temp);

        PriorityQueue<HeapNode> heap = new PriorityQueue<>(
                Comparator.comparingInt(n -> n.filme.getId()));

        for (RunReader r : leitores) {
            Filme f = r.proximo();
            if (f != null) {
                heap.add(new HeapNode(f, r));
            }
        }

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temp)))) {

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

    private void escreverArquivoFinal(File runFinal) throws IOException {
        List<Filme> filmes = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(runFinal)))) {
            while (true) {
                int tamanho;
                try {
                    tamanho = dis.readInt();
                } catch (EOFException e) {
                    break;
                }
                byte[] dados = new byte[tamanho];
                dis.readFully(dados);
                filmes.add(Filme.fromByteArray(dados));
            }
        } catch (Exception e) {
            throw new IOException("Erro ao ler run final", e);
        }

        try (RandomAccessFile raf = new RandomAccessFile(destino, "rw")) {
            raf.setLength(0); 
            raf.writeInt(filmes.size());

            for (Filme f : filmes) {
                byte[] dados = f.toByteArray();
                raf.writeByte(0);
                raf.writeInt(dados.length);
                raf.write(dados);
            }
        }
    }

    private static class RunReader {
        private DataInputStream dis;

        RunReader(File arquivo) throws IOException {
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(arquivo)));
        }

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

    private static class HeapNode {
        Filme filme;
        RunReader leitor;

        HeapNode(Filme filme, RunReader leitor) {
            this.filme = filme;
            this.leitor = leitor;
        }
    }
}