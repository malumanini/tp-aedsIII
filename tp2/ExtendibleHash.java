import java.io.*;

public class ExtendibleHash {
    private static final int HEADER_SIZE = 4 + 4 + 8;

    private RandomAccessFile raf;
    private int profundidadeGlobal;
    private int capacidadeBucket;
    private long diretorioOffset;

    // Abre (ou cria, se nao existir) o arquivo de indice.
    // Se o arquivo ja existir, a capacidade salva nele e usada (o parametro e ignorado nesse caso).
    public ExtendibleHash(String caminhoArquivo, int capacidadeBucketPadrao) throws IOException {
        File f = new File(caminhoArquivo);
        boolean novo = !f.exists() || f.length() == 0;

        this.raf = new RandomAccessFile(caminhoArquivo, "rw");

        if (novo) {
            this.capacidadeBucket = capacidadeBucketPadrao;
            this.profundidadeGlobal = 0; // comeca com 1 unico bucket (2^0 = 1 entrada no diretorio)

            // reserva o espaco do header ANTES de alocar qualquer bucket/diretorio,
            // senao alocarBucket() usaria offset 0 (arquivo ainda vazio) e o header
            // escrito depois sobrescreveria o proprio bucket
            this.diretorioOffset = 0; // valor temporario, corrigido logo abaixo
            escreverHeader();

            HashBucket bucketInicial = new HashBucket();
            bucketInicial.profundidadeLocal = 0;
            long offsetBucketInicial = alocarBucket(bucketInicial);

            this.diretorioOffset = alocarDiretorio(new long[]{offsetBucketInicial});
            escreverHeader();
        } else {
            lerHeader();
        }
    }

    private void lerHeader() throws IOException {
        raf.seek(0);
        profundidadeGlobal = raf.readInt();
        capacidadeBucket = raf.readInt();
        diretorioOffset = raf.readLong();
    }

    private void escreverHeader() throws IOException {
        raf.seek(0);
        raf.writeInt(profundidadeGlobal);
        raf.writeInt(capacidadeBucket);
        raf.writeLong(diretorioOffset);
    }

    private long alocarBucket(HashBucket bucket) throws IOException {
        long offset = raf.length();
        raf.seek(offset);
        raf.write(bucket.toByteArray(capacidadeBucket));
        return offset;
    }

    private void escreverBucket(long offset, HashBucket bucket) throws IOException {
        raf.seek(offset);
        raf.write(bucket.toByteArray(capacidadeBucket));
    }

    private HashBucket lerBucket(long offset) throws IOException {
        raf.seek(offset);
        byte[] dados = new byte[HashBucket.tamanhoBucket(capacidadeBucket)];
        raf.readFully(dados);
        return HashBucket.fromByteArray(dados, capacidadeBucket);
    }

    private long alocarDiretorio(long[] diretorio) throws IOException {
        long offset = raf.length();
        raf.seek(offset);
        for (long v : diretorio) raf.writeLong(v);
        return offset;
    }

    private long[] lerDiretorio() throws IOException {
        int tamanho = 1 << profundidadeGlobal;
        long[] diretorio = new long[tamanho];
        raf.seek(diretorioOffset);
        for (int i = 0; i < tamanho; i++) diretorio[i] = raf.readLong();
        return diretorio;
    }

    // h(k) = k mod 2^p
    private int hash(int chave, int profundidade) {
        return chave % (1 << profundidade);
    }

    // Busca a posicao (em filmes.dat) do registro com o id informado. Retorna -1 se nao encontrado.
    public long buscar(int chave) throws IOException {
        int idx = hash(chave, profundidadeGlobal);
        raf.seek(diretorioOffset + (long) idx * 8);
        long offsetBucket = raf.readLong();

        HashBucket bucket = lerBucket(offsetBucket);
        for (int i = 0; i < bucket.chaves.size(); i++) {
            if (bucket.chaves.get(i) == chave) return bucket.posicoes.get(i);
        }
        return -1;
    }

    // Insere (ou atualiza, se o id ja existir) o par id -> posicao.
    public void inserir(int chave, long posicao) throws IOException {
        int idx = hash(chave, profundidadeGlobal);
        raf.seek(diretorioOffset + (long) idx * 8);
        long offsetBucket = raf.readLong();

        HashBucket bucket = lerBucket(offsetBucket);

        int posExistente = bucket.chaves.indexOf(chave);
        if (posExistente != -1) {
            bucket.posicoes.set(posExistente, posicao);
            escreverBucket(offsetBucket, bucket);
            return;
        }

        if (bucket.chaves.size() < capacidadeBucket) {
            bucket.chaves.add(chave);
            bucket.posicoes.add(posicao);
            escreverBucket(offsetBucket, bucket);
            return;
        }

        // bucket cheio -> divide e tenta inserir de novo
        splitBucket(idx, offsetBucket, bucket);
        inserir(chave, posicao);
    }

    // Divide um bucket cheio em dois, redistribuindo suas chaves conforme o novo bit do hash.
    // Se a profundidade local ja alcancou a profundidade global, o diretorio e duplicado antes.
    private void splitBucket(int idx, long offsetAntigo, HashBucket bucketAntigo) throws IOException {
        int novaProfundidadeLocal = bucketAntigo.profundidadeLocal + 1;

        if (novaProfundidadeLocal > profundidadeGlobal) {
            duplicarDiretorio();
        }

        // padrao de bits (baixa ordem) comum a todas as entradas do diretorio que apontam pra esse bucket
        int mascaraAntiga = (1 << bucketAntigo.profundidadeLocal) - 1;
        int baseLocal = idx & mascaraAntiga;

        int indiceBaixo = baseLocal;                                         // novo bit = 0
        int indiceAlto = baseLocal | (1 << (novaProfundidadeLocal - 1));      // novo bit = 1

        HashBucket bucketBaixo = new HashBucket();
        bucketBaixo.profundidadeLocal = novaProfundidadeLocal;
        HashBucket bucketAlto = new HashBucket();
        bucketAlto.profundidadeLocal = novaProfundidadeLocal;

        for (int i = 0; i < bucketAntigo.chaves.size(); i++) {
            int chave = bucketAntigo.chaves.get(i);
            long pos = bucketAntigo.posicoes.get(i);
            int bit = (chave >> (novaProfundidadeLocal - 1)) & 1;
            if (bit == 0) {
                bucketBaixo.chaves.add(chave);
                bucketBaixo.posicoes.add(pos);
            } else {
                bucketAlto.chaves.add(chave);
                bucketAlto.posicoes.add(pos);
            }
        }

        // reaproveita o offset antigo para o bucket "baixo"; aloca um novo offset pro bucket "alto"
        escreverBucket(offsetAntigo, bucketBaixo);
        long offsetAlto = alocarBucket(bucketAlto);

        // atualiza todas as entradas do diretorio que apontavam pro bucket antigo
        long[] diretorio = lerDiretorio();
        int maskNovo = (1 << novaProfundidadeLocal) - 1;
        for (int i = 0; i < diretorio.length; i++) {
            if (diretorio[i] == offsetAntigo) {
                int padraoLocal = i & maskNovo;
                long novoOffset = (padraoLocal == indiceAlto) ? offsetAlto : offsetAntigo;
                if (diretorio[i] != novoOffset) {
                    raf.seek(diretorioOffset + (long) i * 8);
                    raf.writeLong(novoOffset);
                }
            }
        }
    }

    // Dobra o diretorio: cada entrada antiga passa a ter duas copias (uma para cada valor do novo bit).
    private void duplicarDiretorio() throws IOException {
        long[] antigo = lerDiretorio();
        long[] novo = new long[antigo.length * 2];
        for (int i = 0; i < antigo.length; i++) {
            novo[i] = antigo[i];
            novo[i + antigo.length] = antigo[i];
        }

        long novoOffset = alocarDiretorio(novo);
        diretorioOffset = novoOffset;
        profundidadeGlobal++;
        escreverHeader();
    }

    // Remocao simplificada: remove a chave do bucket correspondente, sem fundir buckets irmaos
    // (mesma simplificacao assumida na Arvore B+, para manter o escopo do TP viavel).
    public boolean remover(int chave) throws IOException {
        int idx = hash(chave, profundidadeGlobal);
        raf.seek(diretorioOffset + (long) idx * 8);
        long offsetBucket = raf.readLong();

        HashBucket bucket = lerBucket(offsetBucket);
        int pos = bucket.chaves.indexOf(chave);
        if (pos == -1) return false;

        bucket.chaves.remove(pos);
        bucket.posicoes.remove(pos);
        escreverBucket(offsetBucket, bucket);
        return true;
    }

    public int getProfundidadeGlobal() {
        return profundidadeGlobal;
    }

    public void close() throws IOException {
        raf.close();
    }
}