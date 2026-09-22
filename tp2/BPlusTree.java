import java.io.*;

// Indice em Arvore B+ sobre o campo "id" dos filmes.
// Cada entrada da arvore mapeia id -> posicao do registro em filmes.dat,
// permitindo busca em O(log n) em vez da varredura sequencial O(n) do FilmeDAO original.
//
// Estrutura do arquivo de indice:
//   [Header]
//     long raizOffset  (offset do no raiz dentro deste arquivo; -1 se a arvore estiver vazia)
//     int  ordem        (ordem da arvore, definida na criacao)
//   [No 1][No 2]...      (cada no tem tamanho fixo, ver BPlusTreeNode.tamanhoNo)
public class BPlusTree {
    private static final int HEADER_SIZE = 8 + 4; // long + int

    private RandomAccessFile raf;
    private long raizOffset;
    private int ordem;

    // Abre (ou cria, se nao existir) o arquivo de indice.
    // Se o arquivo ja existir, a ordem salva nele e usada (o parametro e ignorado nesse caso).
    public BPlusTree(String caminhoArquivo, int ordemPadrao) throws IOException {
        File f = new File(caminhoArquivo);
        boolean novo = !f.exists() || f.length() == 0;

        this.raf = new RandomAccessFile(caminhoArquivo, "rw");

        if (novo) {
            this.ordem = ordemPadrao;
            this.raizOffset = -1;
            escreverHeader();
        } else {
            lerHeader();
        }
    }

    private void lerHeader() throws IOException {
        raf.seek(0);
        raizOffset = raf.readLong();
        ordem = raf.readInt();
    }

    private void escreverHeader() throws IOException {
        raf.seek(0);
        raf.writeLong(raizOffset);
        raf.writeInt(ordem);
    }

    private long alocarNo(BPlusTreeNode no) throws IOException {
        long offset = raf.length();
        raf.seek(offset);
        raf.write(no.toByteArray(ordem));
        return offset;
    }

    private void escreverNo(long offset, BPlusTreeNode no) throws IOException {
        raf.seek(offset);
        raf.write(no.toByteArray(ordem));
    }

    private BPlusTreeNode lerNo(long offset) throws IOException {
        raf.seek(offset);
        byte[] dados = new byte[BPlusTreeNode.tamanhoNo(ordem)];
        raf.readFully(dados);
        return BPlusTreeNode.fromByteArray(dados, ordem);
    }

    // Busca a posicao (em filmes.dat) do registro com o id informado.
    // Retorna -1 se o id nao estiver indexado.
    public long buscar(int chave) throws IOException {
        if (raizOffset == -1) return -1;

        BPlusTreeNode no = lerNo(raizOffset);
        while (!no.folha) {
            int idx = 0;
            while (idx < no.chaves.size() && chave >= no.chaves.get(idx)) idx++;
            no = lerNo(no.ponteiros.get(idx));
        }

        for (int i = 0; i < no.chaves.size(); i++) {
            if (no.chaves.get(i) == chave) return no.ponteiros.get(i);
        }
        return -1;
    }

    // Insere (ou atualiza, se o id ja existir) o par id -> posicao.
    public void inserir(int chave, long posicao) throws IOException {
        if (raizOffset == -1) {
            BPlusTreeNode raiz = new BPlusTreeNode();
            raiz.folha = true;
            raiz.chaves.add(chave);
            raiz.ponteiros.add(posicao);
            raizOffset = alocarNo(raiz);
            escreverHeader();
            return;
        }

        SplitResult sr = inserirRec(raizOffset, chave, posicao);
        if (sr != null) {
            // a raiz estourou -> cresce a arvore em altura, criando uma nova raiz
            BPlusTreeNode novaRaiz = new BPlusTreeNode();
            novaRaiz.folha = false;
            novaRaiz.chaves.add(sr.chavePromovida);
            novaRaiz.ponteiros.add(raizOffset);
            novaRaiz.ponteiros.add(sr.offsetNovoNo);
            raizOffset = alocarNo(novaRaiz);
            escreverHeader();
        }
    }

    // Guarda o resultado de um split: a chave que sobe para o pai e o offset do novo no (direita)
    private static class SplitResult {
        int chavePromovida;
        long offsetNovoNo;
        SplitResult(int c, long o) { chavePromovida = c; offsetNovoNo = o; }
    }

    private SplitResult inserirRec(long offsetNo, int chave, long posicao) throws IOException {
        BPlusTreeNode no = lerNo(offsetNo);

        if (no.folha) {
            int idx = 0;
            while (idx < no.chaves.size() && chave > no.chaves.get(idx)) idx++;

            // id ja existe: apenas atualiza a posicao (ex: registro foi reescrito em outro lugar)
            if (idx < no.chaves.size() && no.chaves.get(idx) == chave) {
                no.ponteiros.set(idx, posicao);
                escreverNo(offsetNo, no);
                return null;
            }

            no.chaves.add(idx, chave);
            no.ponteiros.add(idx, posicao);

            if (no.chaves.size() <= ordem - 1) {
                escreverNo(offsetNo, no);
                return null;
            }
            return splitFolha(offsetNo, no);

        } else {
            int idx = 0;
            while (idx < no.chaves.size() && chave >= no.chaves.get(idx)) idx++;

            SplitResult sr = inserirRec(no.ponteiros.get(idx), chave, posicao);
            if (sr == null) return null;

            no.chaves.add(idx, sr.chavePromovida);
            no.ponteiros.add(idx + 1, sr.offsetNovoNo);

            if (no.chaves.size() <= ordem - 1) {
                escreverNo(offsetNo, no);
                return null;
            }
            return splitInterno(offsetNo, no);
        }
    }

    // Divide uma folha cheia em duas. A folha da esquerda reaproveita o offset original;
    // a nova folha (direita) e alocada no final do arquivo. O encadeamento entre folhas e mantido.
    private SplitResult splitFolha(long offsetNo, BPlusTreeNode no) throws IOException {
        int meio = no.chaves.size() / 2;

        BPlusTreeNode direita = new BPlusTreeNode();
        direita.folha = true;
        direita.chaves.addAll(no.chaves.subList(meio, no.chaves.size()));
        direita.ponteiros.addAll(no.ponteiros.subList(meio, no.ponteiros.size()));
        direita.proximaFolha = no.proximaFolha;

        BPlusTreeNode esquerda = new BPlusTreeNode();
        esquerda.folha = true;
        esquerda.chaves.addAll(no.chaves.subList(0, meio));
        esquerda.ponteiros.addAll(no.ponteiros.subList(0, meio));

        long offsetDireita = alocarNo(direita);
        esquerda.proximaFolha = offsetDireita;
        escreverNo(offsetNo, esquerda);

        // na B+, a chave promovida continua tambem na folha direita (diferente da B tradicional)
        return new SplitResult(direita.chaves.get(0), offsetDireita);
    }

    // Divide um no interno cheio em dois. A chave do meio sobe para o pai e NAO se repete
    // (diferente do split de folha).
    private SplitResult splitInterno(long offsetNo, BPlusTreeNode no) throws IOException {
        int meio = no.chaves.size() / 2;
        int chavePromovida = no.chaves.get(meio);

        BPlusTreeNode direita = new BPlusTreeNode();
        direita.folha = false;
        direita.chaves.addAll(no.chaves.subList(meio + 1, no.chaves.size()));
        direita.ponteiros.addAll(no.ponteiros.subList(meio + 1, no.ponteiros.size()));

        BPlusTreeNode esquerda = new BPlusTreeNode();
        esquerda.folha = false;
        esquerda.chaves.addAll(no.chaves.subList(0, meio));
        esquerda.ponteiros.addAll(no.ponteiros.subList(0, meio + 1));

        long offsetDireita = alocarNo(direita);
        escreverNo(offsetNo, esquerda);

        return new SplitResult(chavePromovida, offsetDireita);
    }

    // Remocao simplificada: remove a chave e o ponteiro correspondentes da folha.
    // NAO faz redistribuicao nem fusao de nos com os irmaos (isso e uma simplificacao
    // assumida para o TP - vale explicar no video que a arvore pode ficar com nos abaixo
    // da ocupacao minima apos varias remocoes, mas a busca continua correta).
    public boolean remover(int chave) throws IOException {
        if (raizOffset == -1) return false;

        long offsetAtual = raizOffset;
        BPlusTreeNode no = lerNo(offsetAtual);
        while (!no.folha) {
            int idx = 0;
            while (idx < no.chaves.size() && chave >= no.chaves.get(idx)) idx++;
            offsetAtual = no.ponteiros.get(idx);
            no = lerNo(offsetAtual);
        }

        for (int i = 0; i < no.chaves.size(); i++) {
            if (no.chaves.get(i) == chave) {
                no.chaves.remove(i);
                no.ponteiros.remove(i);
                escreverNo(offsetAtual, no);
                return true;
            }
        }
        return false;
    }

    // Percorre todas as folhas em ordem (usando o encadeamento), util para debug e para o video
    public void imprimirEmOrdem() throws IOException {
        if (raizOffset == -1) {
            System.out.println("(indice vazio)");
            return;
        }

        BPlusTreeNode no = lerNo(raizOffset);
        while (!no.folha) {
            no = lerNo(no.ponteiros.get(0));
        }

        while (true) {
            for (int i = 0; i < no.chaves.size(); i++) {
                System.out.println("id=" + no.chaves.get(i) + " -> posicao=" + no.ponteiros.get(i));
            }
            if (no.proximaFolha == -1) break;
            no = lerNo(no.proximaFolha);
        }
    }

    public void close() throws IOException {
        raf.close();
    }
}