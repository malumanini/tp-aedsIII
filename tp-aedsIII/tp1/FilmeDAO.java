import java.io.*;

public class FilmeDAO {
    private String caminhoArquivo;
    
    public FilmeDAO(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
    }
    
    public Filme lerPorId(int id) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(4); 
            
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                Filme filme = Filme.fromByteArray(dados);
                if (filme.getId() == id && lapide == 0) {
                    return filme;
                }
            }
        }
        return null;
    }

    public int criarFilme(Filme filme) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {

            raf.seek(0);
            int ultimoId = raf.readInt();
            int novoId = ultimoId + 1;
            
            filme.setId(novoId);
            byte[] dados = filme.toByteArray();
            
            raf.seek(raf.length());
            raf.writeByte(0);
            raf.writeInt(dados.length);
            raf.write(dados);
            
            raf.seek(0);
            raf.writeInt(novoId);
            
            return novoId;
        }
    }

    public boolean atualizarFilme(Filme filmeAtualizado) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {
            raf.seek(4);
            
            while (raf.getFilePointer() < raf.length()) {
                long posicaoLapide = raf.getFilePointer();
                byte lapideAntiga = raf.readByte();
                int tamanhoAntigo = raf.readInt();
                
                if (lapideAntiga == 0) { 
                    byte[] dadosAntigos = new byte[tamanhoAntigo];
                    raf.readFully(dadosAntigos);
                    
                    Filme filmeAntigo = Filme.fromByteArray(dadosAntigos);
                    
                    if (filmeAntigo.getId() == filmeAtualizado.getId()) {
                        byte[] dadosNovos = filmeAtualizado.toByteArray();
                        
                        if (dadosNovos.length == tamanhoAntigo) {
                            raf.seek(posicaoLapide + 5);
                            raf.write(dadosNovos);
                            return true;
                        }
                        else {
                            raf.seek(posicaoLapide);
                            raf.writeByte(1);
                            raf.seek(raf.length());
                            raf.writeByte(0);
                            raf.writeInt(dadosNovos.length);
                            raf.write(dadosNovos);
                            return true;
                        }
                    }
                } else {
                    raf.skipBytes(tamanhoAntigo);
                }
            }
        }
        return false; 
    }

        public boolean deletarFilme(int id) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "rw")) {
            raf.seek(4);
            
            while (raf.getFilePointer() < raf.length()) {
                long posicaoLapide = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    if (filme.getId() == id) {
                        raf.seek(posicaoLapide);
                        raf.writeByte(1); 
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public void listarTodos() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(caminhoArquivo, "r")) {
            raf.seek(4);
            int contador = 0;
            
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                if (lapide == 0) {
                    Filme filme = Filme.fromByteArray(dados);
                    System.out.println("[" + filme.getId() + "] " + filme.getMovieTitle());
                    contador++;
                }
            }
            System.out.println("\nTotal de filmes válidos: " + contador);
        }
    }
}