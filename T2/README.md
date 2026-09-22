# DigestCalculator

Trabalho 2 da disciplina **INF1416 – Segurança da Informação**  
Departamento de Informática – PUC-Rio  
Professor: Anderson Oliveira da Silva  

---

## 1. Visão Geral

O **`DigestCalculator`** é uma aplicação de linha de comando em Java projetada para calcular e verificar a integridade criptográfica de arquivos em uma pasta, utilizando a **Java Cryptography Architecture (JCA)** e mantendo um catálogo persistente em formato **XML**.

O programa foi construído com foco em:
* **Compatibilidade estrita com Sun JDK 1.8 (Java 8)**.
* **Leitura segura em streaming**: arquivos grandes são processados em blocos de bytes via buffer, evitando estouro de memória e sem carregar arquivos inteiros para a RAM.
* **Cálculo sobre o conteúdo**: o hash é gerado estritamente a partir do conteúdo dos arquivos, nunca de seus nomes.
* **Prevenção de colisões**: arquivos com resumos colidentes são detectados e impedidos de corromper o catálogo XML.

---

## 2. Requisitos de Ambiente e Compilação

* **Java Development Kit (JDK):** Versão 1.8 (Java 8) ou superior (compatível com compilação para bytecode Java 8).

### Como Compilar

No terminal, dentro da pasta `T2`:

```bash
javac DigestCalculator.java
```

> **Dica para JDKs modernos (ex: Java 11/17/21):** Para garantir compatibilidade binária 100% estrita com o Java 8, você pode compilar com:
> ```bash
> javac --release 8 DigestCalculator.java
> ```

---

## 3. Sintaxe de Execução

O programa deve ser invocado exclusivamente via linha de comando no seguinte formato:

```bash
java DigestCalculator <Tipo_Digest> <Caminho_ArqListaDigest> <Caminho_da_Pasta_dos_Arquivos>
```

### Descrição dos Argumentos:

| Argumento | Descrição | Valores Aceitos / Exemplo |
| :--- | :--- | :--- |
| `<Tipo_Digest>` | Algoritmo criptográfico de resumo | `MD5`, `SHA1`, `SHA256` ou `SHA512` |
| `<Caminho_ArqListaDigest>` | Caminho para o arquivo XML com o catálogo de digests | Ex: `catalogo.xml` ou `dados/lista.xml` |
| `<Caminho_da_Pasta_dos_Arquivos>` | Caminho da pasta contendo os arquivos a serem verificados | Ex: `./arquivos` ou `/tmp/meus_arquivos` |

> Caso os argumentos sejam omitidos, insuficientes ou inválidos, o programa exibe uma mensagem de orientação de uso e finaliza com código de saída `1`.

---

## 4. Como Rodar se Você Não Possui um Arquivo XML Inicial

Você **não precisa criar previamente nenhum arquivo XML**. O `DigestCalculator` foi desenvolvido para detectar a ausência do arquivo e criá-lo automaticamente do zero!

### Passo a Passo Prático:

#### Passo 1: Crie uma pasta de testes e adicione arquivos de exemplo
```bash
mkdir pasta_teste
echo "Conteudo do primeiro arquivo" > pasta_teste/Arquivo1.dat
echo "Conteudo do segundo arquivo" > pasta_teste/Arquivo2.dat
```

#### Passo 2: Execute informando o nome de um XML que ainda não existe
```bash
java DigestCalculator SHA1 catalogo.xml ./pasta_teste
```

**O que vai acontecer:**
1. O programa calcula o SHA1 de cada arquivo da pasta.
2. Como o arquivo `catalogo.xml` ainda não existia, a lista em memória estava vazia.
3. A saída no terminal informará `(NOT FOUND)` para ambos:
   ```text
   Arquivo1.dat SHA1 00be8cf0f215779c1316b25a3d7cb9df7ee660d5 (NOT FOUND)
   Arquivo2.dat SHA1 f903dc804d9c7da79ecfa125301da05cf4b6cecf (NOT FOUND)
   ```
4. O programa cria o arquivo `catalogo.xml` contendo a tag `<CATALOG>` e os dois registros!

#### Passo 3: Execute o mesmo comando novamente
```bash
java DigestCalculator SHA1 catalogo.xml ./pasta_teste
```

**Saída esperada:**
```text
Arquivo1.dat SHA1 00be8cf0f215779c1316b25a3d7cb9df7ee660d5 (OK)
Arquivo2.dat SHA1 f903dc804d9c7da79ecfa125301da05cf4b6cecf (OK)
```
Como os arquivos estão cadastrados e seus conteúdos não mudaram, o status agora é **`OK`**.

#### Passo 4: Simulando violação de integridade (`NOT OK`)
Altere o conteúdo de um dos arquivos:
```bash
echo "Conteudo adulterado!" > pasta_teste/Arquivo1.dat
java DigestCalculator SHA1 catalogo.xml ./pasta_teste
```

**Saída esperada:**
```text
Arquivo1.dat SHA1 c8f... (NOT OK)
Arquivo2.dat SHA1 f903dc804d9c7da79ecfa125301da05cf4b6cecf (OK)
```
O programa detecta que o hash mudou, alertando com **`NOT OK`**, sem sobrescrever o registro original no XML.

#### Passo 5: Adicionando outro algoritmo (ex: `MD5`) aos mesmos arquivos
Restaure o arquivo e calcule com `MD5`:
```bash
echo "Conteudo do primeiro arquivo" > pasta_teste/Arquivo1.dat
java DigestCalculator MD5 catalogo.xml ./pasta_teste
```

**Saída esperada:**
```text
Arquivo1.dat MD5 776b9e26e38202d8471a4f027878d6b1 (NOT FOUND)
Arquivo2.dat MD5 e57e79fbe469fef893699b0c53841103 (NOT FOUND)
```
Como o `catalogo.xml` só tinha SHA1 para esses arquivos, o status para MD5 é **`NOT FOUND`**, e as entradas `<DIGEST_ENTRY>` de MD5 são acrescentadas dentro do mesmo `<FILE_ENTRY>` existente de cada arquivo.

---

## 5. Formato do Arquivo XML (`ArqListaDigest`)

O catálogo segue a estrutura XML com indentação de 4 espaços:

```xml
<CATALOG>
    <FILE_ENTRY>
        <FILE_NAME>Arquivo1.dat</FILE_NAME>
        <DIGEST_ENTRY>
            <DIGEST_TYPE>SHA1</DIGEST_TYPE>
            <DIGEST_HEX>00be8cf0f215779c1316b25a3d7cb9df7ee660d5</DIGEST_HEX>
        </DIGEST_ENTRY>
        <DIGEST_ENTRY>
            <DIGEST_TYPE>MD5</DIGEST_TYPE>
            <DIGEST_HEX>776b9e26e38202d8471a4f027878d6b1</DIGEST_HEX>
        </DIGEST_ENTRY>
    </FILE_ENTRY>
</CATALOG>
```

* Cada arquivo pode conter até 4 digests (MD5, SHA1, SHA256, SHA512), em qualquer ordem.
* O nome informado em `<FILE_NAME>` armazena exclusivamente o nome do arquivo, sem caminho de pasta.

---

## 6. Regras de Negócio e Precedência de STATUS

Para cada arquivo regular encontrado na pasta, a classificação do status segue rigorosamente a ordem de precedência:

```
                  ┌───────────────────────────────┐
                  │   Calcula Digest do Arquivo   │
                  └──────────────┬────────────────┘
                                 │
                                 ▼
                     ¿Existe COLISÃO de hash?
                    - Outro arquivo na pasta com
                      mesmo digest; OU
                    - Outro arquivo no XML com
                      mesmo digest e nome diferente?
                                 │
                       SIM ┌─────┴─────┐ NÃO
                           ▼           ▼
                      [COLISION]   ¿Arquivo existe no XML
                     (Não grava     com o tipo solicitado?
                       no XML)         │
                             SIM ┌─────┴─────┐ NÃO
                                 ▼           ▼
                         ¿Hash coincide?  [NOT FOUND]
                           │             (Grava novo digest
                     SIM ┌─┴─┐ NÃO            no XML)
                         ▼   ▼
                       [OK] [NOT OK]
```

### Definições Oficiais dos Status:

1. **`COLISION`** *(Atenção à grafia com um só 'L', conforme especificado no enunciado)*:
   * Ocorre se o digest do arquivo colidir com o digest de **outro arquivo de nome diferente no XML** OU com o digest de **outro arquivo presente na pasta**.
   * Hashes em colisão **nunca** são inseridos no XML.
2. **`OK`**:
   * O hash calculado é idêntico ao hash registrado no XML para aquele arquivo e tipo, sem colisão.
3. **`NOT OK`**:
   * O arquivo existe no XML com aquele tipo, mas o hash calculado difere do cadastrado, sem colisão. O XML não é alterado.
4. **`NOT FOUND`**:
   * O arquivo não está no XML OU está no XML mas ainda não possui registro para o tipo solicitado, sem colisão.
   * O novo digest calculado é automaticamente gravado no XML.

---

## 7. Formato da Saída Padrão

A saída para o terminal (`System.out`) segue rigorosamente o formato estrito exigido pelo professor:

```text
Nome_Arq1 Tipo_Digest Digest_Hex_Arq1 (STATUS)
Nome_Arq2 Tipo_Digest Digest_Hex_Arq2 (STATUS)
...
Nome_ArqN Tipo_Digest Digest_Hex_ArqN (STATUS)
```

Exemplo real:
```text
Arquivo1.dat SHA1 8d901bb3a2840ac030f7dbdd7cb823808858cb2f (OK)
Arquivo2.dat SHA1 c8db093d264aa744d178470ad97aa64e67e84ab9 (NOT OK)
Arquivo3.dat SHA1 da39a3ee5e6b4b0d3255bfef95601890afd80709 (NOT FOUND)
Arquivo4.dat SHA1 8d901bb3a2840ac030f7dbdd7cb823808858cb2f (COLISION)
```

---

## 8. Detalhes Internos de Implementação (JCA e DOM)

* **JCA (`MessageDigest`):**
  A leitura é feita através de blocos de `8192` bytes lidos sequencialmente com `FileInputStream`, repassados ao método `messageDigest.update(buffer, 0, bytesRead)`. Isso cumpre a **Observação 3** e **Observação 4** do enunciado.
* **Conversão Hexadecimal:**
  Utiliza o algoritmo exato do professor baseado em máscara de bits e `Integer.toHexString(0x0100 + (digest[i] & 0x00FF)).substring(1)`.
* **Tratamento DOM/XML:**
  Utiliza as bibliotecas nativas `javax.xml.parsers.DocumentBuilder` e `javax.xml.transform.Transformer`. Inclui uma rotina de higienização de nós vazios (`cleanEmptyTextNodes`) que assegura que a regravação do XML mantenha a indentação limpa e legível, sem corrupção de tags ou inserção de quebras de linha espúrias.
