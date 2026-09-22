# 🎬 Disney Movies — CRUD Sequencial & Ordenação Externa

Trabalho Prático I da disciplina **Algoritmos e Estruturas de Dados III** — PUC Minas, Instituto de Ciências Exatas e Informática.

Sistema em **Java** para carga de uma base de dados em CSV, manipulação **CRUD** (Create, Read, Update, Delete) em **arquivo binário sequencial** e **ordenação externa** por intercalação balanceada.

---

## 📚 Sumário

- [Sobre o projeto](#-sobre-o-projeto)
- [Funcionalidades](#-funcionalidades)
- [Estrutura do repositório](#-estrutura-do-repositório)
- [Estrutura do arquivo binário](#-estrutura-do-arquivo-binário)
- [Pré-requisitos](#-pré-requisitos)
- [Como rodar](#-como-rodar)
- [Como usar](#-como-usar)
- [Ordenação externa](#-ordenação-externa)
- [Tecnologias](#-tecnologias)
- [Autores](#-autores)

---

## 📖 Sobre o projeto

A base de dados escolhida foi o **[Disney Movies Dataset](https://www.kaggle.com/)**, contendo informações sobre filmes do estúdio Disney. Cada filme é representado pela entidade `Filme`, com campos que cobrem os tipos exigidos pelo enunciado:

| Tipo exigido                        | Campo             |
|--------------------------------------|--------------------|
| String de tamanho fixo/variável      | `movieId`, `movieTitle`, `franchise`, `genres`, `rating`, `country` |
| Data                                  | `releaseDate`     |
| Inteiro ou Float                      | `runtimeMinutes`  |
| Lista de valores com separador        | `genres`          |

O sistema lê o CSV, converte cada linha em um objeto `Filme`, serializa em bytes e grava tudo em um **arquivo binário sequencial** (`filmes.dat`), sobre o qual são realizadas as operações de CRUD e, posteriormente, a ordenação externa.

---

## ✅ Funcionalidades

- 📥 **Carga do CSV** para arquivo binário (`filmes.dat`)
- 🔍 **Leitura** de um registro por `id`
- ➕ **Criação** de novos registros
- ✏️ **Atualização** de registros (com realocação automática quando o tamanho do registro muda)
- 🗑️ **Exclusão lógica** de registros via marcação de lápide
- 📋 **Listagem** de todos os registros válidos
- 🔀 **Ordenação externa** por intercalação balanceada (*k*-way merge), parametrizável por número de caminhos e tamanho do bloco em memória primária

---

## 🗂 Estrutura do repositório

```
.
├── Main.java                    # Menu principal (terminal) e orquestração do fluxo
├── Filme.java                   # Entidade + serialização/desserialização em bytes
├── FilmeDAO.java                # CRUD sobre o arquivo binário sequencial
├── CsvLoader.java                # Carga do CSV para o arquivo binário
├── ExternalSort.java            # Ordenação externa por intercalação balanceada
└── disney_movies_dataset.csv    # Base de dados (Kaggle, domínio público)
```

---

## 💾 Estrutura do arquivo binário

Seguindo a especificação do trabalho, o arquivo `filmes.dat` é organizado da seguinte forma:

```
┌─────────────────────────────────────────────┐
│  CABEÇALHO (4 bytes)                         │
│  int → último id utilizado                   │
├─────────────────────────────────────────────┤
│  REGISTRO 1                                  │
│  ├─ Lápide (1 byte)        → 0 = válido,     │
│  │                            1 = excluído   │
│  ├─ Tamanho (4 bytes, int) → tamanho do      │
│  │                            vetor de bytes │
│  └─ Dados (N bytes)        → objeto Filme    │
│                                serializado    │
├─────────────────────────────────────────────┤
│  REGISTRO 2                                  │
│  ...                                         │
└─────────────────────────────────────────────┘
```

Quando um registro é **atualizado** e seu tamanho muda, o registro antigo é marcado como excluído (lápide = 1) e o novo é escrito no final do arquivo — exatamente como pede o enunciado.

---

## ⚙️ Pré-requisitos

- **JDK 17+** instalado ([Adoptium Temurin](https://adoptium.net/) recomendado)

Verifique com:

```bash
java -version
javac -version
```

---

## ▶️ Como rodar

**1. Clone o repositório**

```bash
git clone https://github.com/seu-usuario/tp-aedsIII.git
cd tp-aedsIII
```

> ⚠️ Certifique-se de que o arquivo `disney_movies_dataset.csv` está na mesma pasta dos arquivos `.java`.

**2. Compile**

```bash
javac *.java
```

> No Git Bash/MINGW, use exatamente `*.java` (e não apenas `*`), senão o compilador tentará compilar o próprio CSV.

**3. Execute**

```bash
java Main
```

O menu principal será exibido no terminal:

```
=== MENU PRINCIPAL ===
1. Carregar CSV
2. CRUD Sequencial
3. Ordenacao Externa
4. Sair
```

---

## 🧭 Como usar

### 1️⃣ Carregar a base de dados

Escolha a opção **1** no menu principal para importar o CSV e gerar o arquivo binário `filmes.dat`. Esse passo deve ser feito **antes** de qualquer operação de CRUD ou ordenação.

### 2️⃣ CRUD Sequencial

Escolha a opção **2** para acessar o submenu:

```
=== CRUD ===
1. Ler por ID
2. Criar novo
3. Atualizar
4. Deletar
5. Listar todos
6. Voltar
```

- **Ler por ID** — busca sequencial pelo id informado.
- **Criar novo** — solicita os dados do filme e grava no fim do arquivo com um novo id.
- **Atualizar** — permite editar o título de um filme existente; se o novo registro tiver tamanho diferente do original, ele é realocado para o fim do arquivo.
- **Deletar** — marca o registro como excluído (exclusão lógica via lápide).
- **Listar todos** — percorre o arquivo e exibe todos os registros válidos.

### 3️⃣ Ordenação Externa

Escolha a opção **3** e informe:

- **Número de caminhos (*ways*)** — quantos blocos são intercalados por vez
- **Número máximo de registros por bloco** em memória primária

O algoritmo distribui o arquivo em blocos ordenados (*runs*), intercala-os progressivamente (*k*-way merge) e reescreve o arquivo final **sem os espaços deixados por registros excluídos ou realocados**. A partir daí, todas as operações de CRUD passam a atuar sobre esse arquivo já compactado e ordenado.

---

## 🔀 Ordenação externa

A ordenação segue o clássico algoritmo de **ordenação por intercalação balanceada**:

1. **Distribuição** — o arquivo original é lido em blocos de até *N* registros, cada bloco é ordenado em memória (por `id`) e gravado em um arquivo temporário (*run*).
2. **Intercalação (*merge*)** — os *runs* são agrupados de *k* em *k* (o número de caminhos escolhido) e mesclados usando uma **fila de prioridade (min-heap)**, produzindo *runs* cada vez maiores, até restar apenas um.
3. **Escrita final** — o *run* final é gravado sobre o arquivo original, já sem registros excluídos, com o cabeçalho atualizado para refletir o maior `id` em uso.

---

## 🛠 Tecnologias

- **Java** (E/S de arquivos via `RandomAccessFile`, `DataInputStream`/`DataOutputStream`)
- Sem bibliotecas externas — apenas a biblioteca padrão do Java (`java.io`, `java.time`, `java.util`)

---

## 👥 Autores

- Maria Luiza Manini de Oliveira
- Thayná da Silva Cota

---

## 📄 Licença

Trabalho acadêmico desenvolvido para fins educacionais na disciplina de Algoritmos e Estruturas de Dados III — PUC Minas.