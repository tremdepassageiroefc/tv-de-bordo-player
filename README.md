# TV de Bordo — player para Intelbras Izy Play

App de Android TV que transforma a box num receptor dedicado do canal do trem.

- Abre sozinho quando a box liga e toca `udp://@239.10.10.1:5000` em tela cheia.
- Usa o **libVLC** (o mesmo motor do VLC testado na bancada), com decodificação por hardware.
- **Se recupera sozinho:** sem sinal, mostra "Aguardando sinal" e tenta de novo; se o vídeo
  parar (servidor reiniciou, cabo, switch), reconecta em segundos.
- **Modo quiosque:** o botão Voltar é ignorado e o passageiro não sai do canal.
- Tela de espera com a identificação da TV (ex.: "Carro 3 · TV 1") e o IP da box, para facilitar a manutenção.
- **Configurações escondidas:** aperte **OK 5 vezes seguidas** (em até 3 s) no controle.

---

## 1. Gerar o APK

Escolha **uma** das opções.

### Opção A — GitHub (sem instalar nada no PC)

1. Crie um repositório no GitHub (pode ser privado) e envie o conteúdo desta pasta.
   Pelo site: **Add file → Upload files**, arraste tudo e confirme.
   A pasta `.github` precisa ir junto (no Windows, ative "mostrar arquivos ocultos").
2. A compilação começa sozinha. Acompanhe na aba **Actions** (leva de 3 a 6 min).
3. Ao terminar, abra a execução e baixe **tv-de-bordo-apk** na seção **Artifacts**.
   Vem um `.zip` com o `.apk` dentro.

Para recompilar sem mudar nada: **Actions → Compilar APK → Run workflow**.

### Opção B — Android Studio

1. Instale o Android Studio e abra esta pasta (**File → Open**).
2. Espere o Gradle sincronizar (na primeira vez ele baixa as dependências).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/release/` (ou `debug/`).

O APK de release sai assinado com a chave de debug, o que basta para instalar nas boxes.

---

## 2. Instalar na Izy Play

### Pelo pendrive
1. Copie o `.apk` para um pendrive e ligue na USB da box.
2. Instale um gerenciador de arquivos pela Play Store (ex.: X-plore ou File Commander).
3. Abra o APK pelo gerenciador. Na primeira vez, o Android pede para **permitir fontes
   desconhecidas** para esse gerenciador: permita e volte.

### Pela rede, com ADB (instala em todas as boxes de uma vez)
```bash
adb connect 192.168.50.101:5555
adb -s 192.168.50.101:5555 install -r tv-de-bordo.apk
```

---

## 3. Primeira configuração em cada box

1. Abra **TV de Bordo** na tela inicial. Sem o servidor transmitindo, aparece "Aguardando sinal".
2. Aperte **OK 5x** para abrir as configurações:
   - **Endereço do canal:** `udp://@239.10.10.1:5000` (já vem preenchido)
   - **Identificação:** ex. `Carro 3 · TV 1`
   - **Buffer de rede:** 1000 ms (suba para 1500–2000 se houver travadas)
   - **Reconectar após:** 15 s
   - **Abrir sozinho ao ligar:** marcado
3. Veja a linha **Android** nas informações da tela:
   - **Android 9 (Izy Play Full HD de 1ª geração):** nada mais a fazer, o app abre sozinho ao ligar.
   - **Android 10 ou mais novo:** o Android bloqueia apps abrindo sozinhos no boot, a não ser que
     tenham a permissão **"sobrepor a outros apps"**. Use o botão **Permissão p/ abrir no boot**.
     Se a box não tiver essa tela, conceda pelo PC (uma vez, com ADB):
     ```bash
     adb shell appops set br.tvbordo.player SYSTEM_ALERT_WINDOW allow
     ```
     Sem ADB, use a alternativa do item 4.
4. **Salvar.** Desligue e ligue a box da tomada para conferir que ela volta direto no canal.

### 4. Alternativa: modo "tela inicial" (quiosque completo)

Marque **Usar como tela inicial** e salve. Ao apertar HOME, o Android pergunta qual tela inicial usar:
escolha **TV de Bordo** → **Sempre**. A box passa a ligar direto no app em qualquer versão do
Android, sem precisar da permissão acima.

- Em algumas versões do Android TV a pergunta não aparece. Nesse caso, pelo PC:
  ```bash
  adb shell cmd package set-home-activity br.tvbordo.player/.HomeAlias
  ```
- **Para voltar ao normal:** OK 5x → desmarque **Usar como tela inicial** → Salvar.
  O botão **Sair do app** fecha o player para manutenção.

---

## 5. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| "Aguardando sinal" e o IP mostra `sem rede` | Cabo, switch ou Ethernet desligada na box |
| "Aguardando sinal" com IP certo | Servidor parado, IGMP snooping sem querier ou endereço do canal errado |
| Funciona e cai depois de 3 a 5 min | Falta o **IGMP querier** em um switch (ver `rede/rede.md` do pacote do servidor) |
| Imagem trava ou picota | Aumente o buffer para 1500–2000 ms; confira o bitrate do servidor |
| Não abre ao ligar | Android 10+: falta a permissão do item 3, ou use o modo tela inicial |
| Tela preta com som | Codec: confirme H.264 na saída do servidor (`-c:v libx264`) |

---

## Estrutura do projeto

```
app/src/main/java/br/tvbordo/player/
  PlayerActivity.kt    player em tela cheia, vigia de sinal, OK 5x, quiosque
  SettingsActivity.kt  configurações, permissão de boot, modo tela inicial
  BootReceiver.kt      abre o player quando a box liga
  Prefs.kt             valores salvos (canal, buffer, tempo de reconexão…)
  NetInfo.kt           IP da box para a tela de espera
app/src/main/AndroidManifest.xml
app/build.gradle.kts   libVLC 3.7.6, minSdk 23 (Android 6+), ABIs armeabi-v7a e arm64-v8a
.github/workflows/compilar-apk.yml   compilação automática no GitHub
```

Licença do libVLC: LGPL 2.1. Para uso interno nas boxes, basta manter este aviso.
