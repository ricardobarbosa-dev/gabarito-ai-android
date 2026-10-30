# GabaritoAI Mobile (Android Nativo - Java) 📱🚀

**Assistente Global de Resolução de Questões com Visão Multimodal e Janela Flutuante (Overlay)**

O **GabaritoAI Mobile** é um aplicativo Android nativo completo desenvolvido em **Java** que funciona de forma **global e independente** sobre qualquer aplicativo instalado no seu celular (como o aplicativo oficial da **Estácio**, **Canvas**, **Blackboard**, **Moodle**, simulados em PDF ou navegadores).

---

## 🌟 Como Funciona

```
[ App da Estácio Aberto ] 
          │
          ▼
   🟢 Bolha Flutuante GabaritoAI na borda da tela
          │ (Toque na Bolha)
          ▼
   📸 Captura de Tela Instantânea (MediaProjection API)
          │
          ▼
   🧠 Gemini Multimodal Vision API (gemini-3.5-flash / gemini-2.5-flash)
          │
          ▼
   ✨ Card Flutuante Verde: Alternativa Correta + Justificativa Didática
          │ (Opcional)
          ▼
   🖱️ Auto-Click na Alternativa via Serviço de Acessibilidade
```

1. **Visão Multimodal com Gemini (IA):** Ao tocar na bolha flutuante, o app captura um screenshot sem root via `MediaProjection`, envia direto para o modelo de visão do Google Gemini com prompt Chain-of-Thought determinístico (temperatura 0.0) e identifica a alternativa correta com máxima precisão.
2. **Overlay Global (`WindowManager`):** A bolha flutuante permanece na borda da tela por cima de outros apps (estilo bolha do Messenger) e pode ser arrastada livremente.
3. **Auto-Click Opcional (`AccessibilityService`):** Se ativado, o serviço de acessibilidade inspeciona a árvore nativa do app e clica na alternativa correta automaticamente.

---

## 🛠️ Estrutura do Projeto

```
gabarito-ai-android/
├── .github/workflows/build-apk.yml     # Compilação automática de APK no GitHub
├── app/
│   ├── build.gradle                   # Dependências: OkHttp, Gson, Material3
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml        # Permissões de sobreposição, tela e serviços
│       ├── java/com/gabaritoai/mobile/
│       │   ├── MainActivity.java      # Tela inicial, chave de API e ativação
│       │   ├── ai/
│       │   │   ├── GeminiVisionClient.java   # Cliente OkHttp com Gemini Multimodal
│       │   │   └── SolverCallback.java
│       │   ├── capture/
│       │   │   └── ScreenCaptureService.java # Captura via MediaProjection
│       │   ├── model/
│       │   │   └── QuestionResult.java       # POJO com resposta e raciocínio
│       │   ├── overlay/
│       │   │   └── FloatingBubbleService.java# Foreground Service da bolha flutuante
│       │   ├── service/
│       │   │   └── AutoClickAccessibilityService.java # Clique nativo
│       │   └── utils/
│       │       └── Prefs.java         # SharedPreferences seguro
│       └── res/
│           ├── drawable/              # Estilos visuais e ícones vetoriais
│           ├── layout/
│           │   ├── activity_main.xml         # Interface moderna Dark Slate/Emerald
│           │   ├── layout_floating_bubble.xml# Bolha flutuante com spinner
│           │   └── layout_result_card.xml    # Card flutuante com resposta
│           ├── values/                # Cores, temas e textos em português
│           └── xml/                   # Configuração de acessibilidade
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## 🚀 Como Compilar e Gerar o APK

### Método 1: No Android Studio (Recomendado para Desenvolvimento)
1. Abra o **Android Studio**.
2. Clique em **File > Open** e selecione a pasta `gabarito-ai-android`.
3. Aguarde o Gradle sincronizar as dependências.
4. Conecte seu celular Android via cabo USB (com Depuração USB ativada) ou inicie um Emulador.
5. Clique no botão verde de **Run (Play)** no topo para instalar diretamente no aparelho!
6. Para gerar o arquivo APK: vá no menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Método 2: Pelo GitHub Actions (Sem precisar instalar nada no PC!)
1. Crie um repositório no seu GitHub (público ou privado).
2. Envie os arquivos da pasta `gabarito-ai-android` para lá.
3. A action definida em `.github/workflows/build-apk.yml` iniciará automaticamente.
4. Ao finalizar em ~2 minutos, vá na aba **Actions**, clique no workflow concluído e baixe o arquivo `GabaritoAI-Debug-APK` pronto para instalar no seu celular!

### Método 3: Via Linha de Comando (Gradle Wrapper)
```bash
cd gabarito-ai-android
./gradlew assembleDebug
```
O APK final será gerado em: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📱 Permissões do Android Utilizadas

* `SYSTEM_ALERT_WINDOW`: Permite desenhar a bolha e o card de resposta flutuante sobre outros aplicativos.
* `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Mantém o serviço do assistente ativo em segundo plano enquanto você usa a Estácio.
* `INTERNET`: Comunicação segura direta com os servidores da API do Google Gemini.
* `BIND_ACCESSIBILITY_SERVICE` (Opcional): Permite a leitura e clique automático nas alternativas se o usuário optar por ativar essa facilidade nas configurações de acessibilidade.
