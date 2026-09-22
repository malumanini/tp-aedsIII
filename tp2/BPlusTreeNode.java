import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class BPlusTreeNode {
    public boolean folha;
    public List<Integer> chaves;
    public List<Long> ponteiros; // interno: filhos; folha: posicoes dos registros em filmes.dat
    public long proximaFolha;    // so usado quando folha == true; -1 se nao houver proxima

    public BPlusTreeNode() {
        this.chaves = new ArrayList<>();
        this.ponteiros = new ArrayList<>();
        this.proximaFolha = -1;
    }

    // tamanho fixo (em bytes) de qualquer no da arvore, dada a ordem
    public static int tamanhoNo(int ordem) {
        // 1 (flag folha) + 4 (nChaves) + 4*(ordem-1) chaves + 8*ordem slots de ponteiro
        return 1 + 4 + 4 * (ordem - 1) + 8 * ordem;
    }

    // Serializa o no respeitando a capacidade fixa da ordem.
    // Slots nao usados sao preenchidos com 0 (chaves) ou -1 (ponteiros), so por padronizacao.
    //
    // Layout dos "ponteiros" (sempre ordem slots long):
    //   - no interno: slots[0..ordem-1] = filhos
    //   - no folha:   slots[0..ordem-2] = posicoes dos registros, slots[ordem-1] = proximaFolha
    public byte[] toByteArray(int ordem) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(folha ? 1 : 0);
        dos.writeInt(chaves.size());

        for (int i = 0; i < ordem - 1; i++) {
            dos.writeInt(i < chaves.size() ? chaves.get(i) : 0);
        }

        if (folha) {
            for (int i = 0; i < ordem - 1; i++) {
                dos.writeLong(i < ponteiros.size() ? ponteiros.get(i) : -1L);
            }
            dos.writeLong(proximaFolha);
        } else {
            for (int i = 0; i < ordem; i++) {
                dos.writeLong(i < ponteiros.size() ? ponteiros.get(i) : -1L);
            }
        }

        dos.flush();
        byte[] resultado = baos.toByteArray();
        dos.close();
        return resultado;
    }

    // Reconstroi um no a partir do vetor de bytes lido do arquivo de indice.
    // A ordem de leitura precisa espelhar exatamente a ordem de escrita do toByteArray.
    public static BPlusTreeNode fromByteArray(byte[] dados, int ordem) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(dados);
        DataInputStream dis = new DataInputStream(bais);

        BPlusTreeNode no = new BPlusTreeNode();
        no.folha = dis.readByte() == 1;
        int nChaves = dis.readInt();

        int[] todasChaves = new int[ordem - 1];
        for (int i = 0; i < ordem - 1; i++) {
            todasChaves[i] = dis.readInt();
        }
        for (int i = 0; i < nChaves; i++) {
            no.chaves.add(todasChaves[i]);
        }

        if (no.folha) {
            long[] todasPosicoes = new long[ordem - 1];
            for (int i = 0; i < ordem - 1; i++) {
                todasPosicoes[i] = dis.readLong();
            }
            for (int i = 0; i < nChaves; i++) {
                no.ponteiros.add(todasPosicoes[i]);
            }
            no.proximaFolha = dis.readLong();
        } else {
            long[] todosFilhos = new long[ordem];
            for (int i = 0; i < ordem; i++) {
                todosFilhos[i] = dis.readLong();
            }
            // no interno com nChaves chaves tem sempre nChaves + 1 filhos
            for (int i = 0; i < nChaves + 1; i++) {
                no.ponteiros.add(todosFilhos[i]);
            }
        }

        dis.close();
        return no;
    }

    @Override
    public String toString() {
        return (folha ? "Folha" : "Interno") + " chaves=" + chaves +
                " ponteiros=" + ponteiros +
                (folha ? " proximaFolha=" + proximaFolha : "");
    }
}