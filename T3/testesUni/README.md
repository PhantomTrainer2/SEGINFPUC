# Testes automatizados do Cofre Digital

Testes que conferem o roteiro do [`HowToTest.md`](../HowToTest.md) (seções 1 a 8) sem precisar clicar em nada. O que só pode ser conferido por uma pessoa (o celular com o Google Authenticator, a frase sem eco no terminal, o visual das telas e o Word) está na **seção 10 – Testar manualmente!** do `HowToTest.md`.

## Como rodar

No **Git Bash**, a partir da pasta `T3`, com as bibliotecas já em `lib/` (veja o `HowToTest.md`):

```bash
./testesUni/rodar.sh              # todos (~5 min)
./testesUni/rodar.sh unitario     # só os testes sem interface (~10 s)
./testesUni/rodar.sh gui          # a interface e depois o logView (~5 min)
./testesUni/rodar.sh logview      # só o logView (usa o banco gerado pelo gui)
```

* O script roda o `build.sh` antes, compila os testes em `build/testes/classes` e mostra no final `TOTAL: N OK, 0 FALHA(S)`. Cada verificação aparece como `[OK]` ou `[FALHA]`, e o log completo fica em `build/testes/resultado.txt`.
* **Durante o teste da interface, janelas do cofre abrem e se mexem sozinhas. Não clique nelas** nem use o mouse sobre elas.
* O teste da interface leva uns 5 minutos porque espera de verdade o fim dos bloqueios de 2 minutos.
* Tudo roda em `build/testes` (fora do git). O `cofre.db` da pasta `T3` e o `Pacote-T3` **não são alterados**: as adulterações são feitas em cópias.

## O que cada teste cobre

| Arquivo | Cobertura |
| :--- | :--- |
| `TesteUnitario.java` | TOTP com os vetores oficiais do RFC 6238 e margem de ±30 s; `TOTP.java` importando só `Mac`, `SecretKeySpec` e `Date`; Base32; campos dos três certificados; chaves privadas (frase certa/errada, chave de outro usuário); crítica da senha; hash do exemplo do enunciado no bcrypt; busca das 2^n combinações do teclado; cadastro no banco (bcrypt `$2y$08$`, chave TOTP cifrada, Chaveiro com PEM e chave binária, KID); as cinco tabelas e os 64 códigos de mensagem; erros e bloqueios; listagem, controle de acesso e 12 adulterações da pasta segura. |
| `TesteGui.java` | Abre o cofre de verdade, preenche os campos, clica nos botões, lê os diálogos e confere os códigos gravados em `Registros` depois de cada ação. São 5 execuções seguidas do sistema: (1) primeira execução, cadastros, etapas 1–3, Tela Principal, Consulta, Saída e bloqueios; (2) frase do admin errada; (3) Cancelar; (4) partida normal, persistência, compartilhamento, adulterações pela interface e Encerrar Sistema; (5) total de acessos persistido e X com sessão aberta. O código TOTP é calculado com a chave mostrada no cadastro, inclusive o do intervalo anterior (−30 s, aceito) e o de dois intervalos atrás (−60 s, rejeitado). |
| `TesteLogView.java` | Autenticação do `logView` (só a chave do admin passa), registros com data, hora e código em ordem cronológica, `<login_name>`/`<arq_name>` substituídos, mensagens de uso e de caminho inválido, e a separação entre `cofre.jar` e `logview.jar`. A leitura da frase sem eco precisa de um terminal real e é conferida manualmente. |
| `rodar.sh` | Compila e executa os testes na ordem certa e soma os resultados. |

Os testes ficam no pacote padrão, como as classes do projeto, para acessar os membros package-private. Não entram no `cofre.jar` nem no `logview.jar`: o `build.sh` compila só a pasta `src`.
