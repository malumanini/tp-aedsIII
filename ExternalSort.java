import java.io.*;
import java.util.*;

public class ExternalSort {
    private String origem;
    private String destino;
    private int maxReg;
    private int ways;
    
    public ExternalSort(String origem, String destino, int maxReg, int ways) {
        this.origem = origem;
        this.destino = destino;
        this.maxReg = maxReg;
        this.ways = ways;
    }
    
    public void ordenar() throws IOException {
        System.out.println("Iniciando ordenacao...");
        System.out.println("Origem: " + origem);
        System.out.println("Destino: " + destino);
        System.out.println("Max regs: " + maxReg);
        System.out.println("Ways: " + ways);
        
        System.out.println("Lendo arquivo...");
        List<Filme> filmes = lerFilmesValidos();
        
        System.out.println("Encontrados " + filmes.size() + " filmes validos");
        
        System.out.println("Ordenando...");
        filmes.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        
        System.out.println("Salvando arquivo ordenado...");
        salvarOrdenado(filmes);
        
        System.out.println("Concluido!");
    }
    
    private List<Filme> lerFilmesValidos() throws IOException {
        List<Filme> lista = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(origem, "r")) {
            raf.seek(4);
            
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanho = raf.readInt();
                byte[] dados = new byte[tamanho];
                raf.readFully(dados);
                
                if (lapide == 0) {
                    try {
                        Filme f = Filme.fromByteArray(dados);
                        lista.add(f);
                    } catch (Exception e) {
                    }
                }
            }
        }
        
        return lista;
    }
    
    private void salvarOrdenado(List<Filme> filmes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(destino, "rw")) {
            raf.writeInt(filmes.size());
            
            for (Filme f : filmes) {
                byte[] dados = f.toByteArray();
                raf.writeByte(0);
                raf.writeInt(dados.length);
                raf.write(dados);
            }
        }
    }
}