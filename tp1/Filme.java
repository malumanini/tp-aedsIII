import java.io.*;
import java.time.LocalDate;

// Entidade que representa um filme. Cobre os 5 tipos de campo exigidos pelo TP:
// movieId = string de tamanho fixo, movieTitle = string de tamanho variavel,
// releaseDate = data, genres = lista com separador, runtimeMinutes = inteiro
public class Filme {
    private int id;
    private String movieId;
    private String movieTitle;
    private String franchise;
    private LocalDate releaseDate;
    private String genres;
    private int runtimeMinutes;
    private String rating;
    private String country;

    public Filme() {}

    public Filme(int id, String movieId, String movieTitle, String franchise,
                 LocalDate releaseDate, String genres, int runtimeMinutes,
                 String rating, String country) {
        this.id = id;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.franchise = franchise;
        this.releaseDate = releaseDate;
        this.genres = genres;
        this.runtimeMinutes = runtimeMinutes;
        this.rating = rating;
        this.country = country;
    }

    // getters e setters padrao de cada atributo
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = movieId; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getFranchise() { return franchise; }
    public void setFranchise(String franchise) { this.franchise = franchise; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public String getGenres() { return genres; }
    public void setGenres(String genres) { this.genres = genres; }

    public int getRuntimeMinutes() { return runtimeMinutes; }
    public void setRuntimeMinutes(int runtimeMinutes) { this.runtimeMinutes = runtimeMinutes; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    @Override
    public String toString() {
        return "Filme{" +
                "id=" + id +
                ", movieId='" + movieId + '\'' +
                ", movieTitle='" + movieTitle + '\'' +
                ", franchise='" + franchise + '\'' +
                ", releaseDate=" + releaseDate +
                ", genres='" + genres + '\'' +
                ", runtimeMinutes=" + runtimeMinutes +
                ", rating='" + rating + '\'' +
                ", country='" + country + '\'' +
                '}';
    }

    // serializa o filme para um vetor de bytes, para ser gravado no arquivo binario.
    // writeUTF ja grava o tamanho de cada string antes do conteudo, entao nao
    // precisamos controlar isso manualmente ao ler de volta
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(id);
        dos.writeUTF(movieId);
        dos.writeUTF(movieTitle);
        dos.writeUTF(franchise);
        dos.writeLong(releaseDate.toEpochDay()); // data guardada como numero de dias desde 1970
        dos.writeUTF(genres);
        dos.writeInt(runtimeMinutes);
        dos.writeUTF(rating);
        dos.writeUTF(country);

        dos.flush();
        byte[] resultado = baos.toByteArray();
        dos.close();
        
        return resultado;
    }

    // reconstroi um Filme a partir do vetor de bytes salvo no arquivo.
    // a ordem de leitura precisa ser exatamente igual a ordem de escrita do toByteArray
    public static Filme fromByteArray(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        Filme filme = new Filme();
        
        filme.id = dis.readInt();
        filme.movieId = dis.readUTF();
        filme.movieTitle = dis.readUTF();
        filme.franchise = dis.readUTF();
        filme.releaseDate = LocalDate.ofEpochDay(dis.readLong());
        filme.genres = dis.readUTF();
        filme.runtimeMinutes = dis.readInt();
        filme.rating = dis.readUTF();
        filme.country = dis.readUTF();

        dis.close();
        return filme;
    }
}