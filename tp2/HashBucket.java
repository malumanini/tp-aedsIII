import java.io.*;
import java.util.ArrayList;
import java.util.List;

// Representa um bucket do Hashing Estendido: guarda pares (id, posicao) ate a capacidade maxima,
// mais a profundidade local (quantos bits do hash esse bucket "conhece").
//
// O bucket tem tamanho FIXO em bytes (baseado na capacidade maxima), o que permite sobrescrever
// no mesmo offset do arquivo sem precisar realocar espaco.
public class HashBucket {
    public int profundidadeLocal;
    public List<Integer> chaves;
    public List<Long> posicoes;

    public HashBucket() {
        this.chaves = new ArrayList<>();
        this.posicoes = new ArrayList<>();
    }

    // tamanho fixo (em bytes) de qualquer bucket, dada a capacidade maxima de registros
    public static int tamanhoBucket(int capacidade) {
        // 4 (profundidadeLocal) + 4 (nChaves) + 4*capacidade (chaves) + 8*capacidade (posicoes)
        return 4 + 4 + 4 * capacidade + 8 * capacidade;
    }

    public byte[] toByteArray(int capacidade) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(profundidadeLocal);
        dos.writeInt(chaves.size());

        for (int i = 0; i < capacidade; i++) {
            dos.writeInt(i < chaves.size() ? chaves.get(i) : 0);
        }
        for (int i = 0; i < capacidade; i++) {
            dos.writeLong(i < posicoes.size() ? posicoes.get(i) : -1L);
        }

        dos.flush();
        byte[] resultado = baos.toByteArray();
        dos.close();
        return resultado;
    }

    public static HashBucket fromByteArray(byte[] dados, int capacidade) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(dados);
        DataInputStream dis = new DataInputStream(bais);

        HashBucket bucket = new HashBucket();
        bucket.profundidadeLocal = dis.readInt();
        int nChaves = dis.readInt();

        int[] todasChaves = new int[capacidade];
        for (int i = 0; i < capacidade; i++) todasChaves[i] = dis.readInt();

        long[] todasPosicoes = new long[capacidade];
        for (int i = 0; i < capacidade; i++) todasPosicoes[i] = dis.readLong();

        for (int i = 0; i < nChaves; i++) {
            bucket.chaves.add(todasChaves[i]);
            bucket.posicoes.add(todasPosicoes[i]);
        }

        dis.close();
        return bucket;
    }
}