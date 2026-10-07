# CatWatch: Guia do Agente

Este documento descreve de forma direta e detalhada o que o aplicativo **CatWatch** faz, como sua arquitetura está organizada, as responsabilidades de cada componente e as restrições técnicas que devem ser respeitadas em qualquer modificação.

---

## 1. Visão Geral do Aplicativo

O **CatWatch** transforma um smartphone Android em uma câmera autônoma de monitoramento contínuo (24/7) para detecção de gatos, priorizando eficiência energética, integridade de memória e baixa emissão térmica.

O sistema opera sem gravação contínua de vídeo. A câmera executa análise leve de imagem em tempo real na memória e dispara fotos pontuais em alta definição apenas quando um felino é detectado.

---

## 2. O que o Aplicativo Faz

### 2.1. Visão Computacional On-Device

* **Classificação em Tempo Real:** Utiliza o Google ML Kit Image Labeling operando localmente no aparelho (sem envio para nuvem e sem necessidade de conexão com a internet).
* **Filtro Semântico:** Identifica e reage apenas a rótulos felinos (`Cat`, `Kitten`, `Felidae`) com índice de confiança igual ou superior a 80% ($\ge 0.80$).
* **Controle de Frequência:** Limita a taxa de inferência a no máximo 2 quadros por segundo (FPS) com descarte prévio de frames, evitando aquecimento do processador.

### 2.2. Estratégia Dual-Snapshot e Anti-Duplicação

* **Foto Inicial ($T_0$):** Disparada imediatamente no momento da detecção (aproximação do animal).
* **Foto de Confirmação ($T_5$):** Agendada automaticamente para 15 segundos após $T_0$, permitindo auditar se o animal permaneceu no local (ex: consumindo água ou ração).
* **Janela de Cooldown (120s):** Após o disparo de um evento, o sistema entra em repouso por 2 minutos, ignorando novas detecções para evitar fotos repetidas do mesmo evento.
* **Armazenamento de Fotos:** As imagens capturadas (720p, compressão JPEG de 80%) são salvas no diretório dedicado e privado do app (`Android/data/.../files/Pictures`) e sincronizadas com a biblioteca do sistema via `MediaScanner`, evitando poluir a galeria pessoal do aparelho.

### 2.3. Persistência e Limpeza Automática

* **Banco de Dados Local (Room):** Registra cada evento na tabela `cat_events`, armazenando identificador, timestamp, caminho do arquivo, confiança da detecção, tipo do evento e flag de confirmação.
* **Expurgo Automático:** Na inicialização, o sistema executa uma limpeza que remove registros e arquivos de imagem com mais de 30 dias de criação para evitar o esgotamento do armazenamento interno.

### 2.4. Operação em Segundo Plano (24/7)

* **Foreground Service:** Mantém o monitoramento ativo mesmo se a interface for colocada em segundo plano através de um serviço em primeiro plano especializado (`foregroundServiceType="camera"`).
* **Notificação Persistente:** Notificação silenciosa de baixa prioridade mantém o processo protegido contra encerramento pelo gerenciador de memória do sistema.

### 2.5. Interface e Experiência do Usuário

* **Seletor Operacional de 3 Estados:** Controle na barra superior que alterna entre:
  * `Desligado`: Câmera desvinculada do ciclo de vida, sem consumo de sensor ou CPU (standby).
  * `Enquadrar`: Visualização da câmera ativa para ajuste de ângulo, com inferência de IA desligada.
  * `Monitorar`: Câmera, IA de detecção e serviço de segundo plano ativos.
  * O estado escolhido é persistido em `SharedPreferences` e restaurado ao reiniciar o app.
* **Painel Deslizante Dinâmico:** Feed inferior que desliza suavemente sobre a visualização da câmera, com arraste tátil 1-a-1 e repouso calibrado, mantendo a camada de vídeo estática para evitar oscilações visuais.
* **Agrupamento em Álbuns por Hora:** As detecções são organizadas por data e agrupadas pela hora do dia (ex: `17:00 ~ 18:00`) em uma grade de duas colunas com contagem de fotos e imagem de capa.
* **Filtros de Período:** Chips de filtro rápido (*Hoje*, *Últimas 24h*, *Ontem*, *Todos*) e seleção por intervalo de datas via calendário.
* **Visualizador em Tela Cheia:** Toque em um álbum abre um diálogo modal imersivo com carrossel horizontal (`ViewPager2`), permitindo deslizar entre as fotos daquela sessão com indicador visual de pontos (*dots*).
* **Exclusão Atômica e em Lote:** Permite apagar fotos individuais no modal ou selecionar múltiplos álbuns para exclusão simultânea no banco de dados e no disco.

---

## 3. Estrutura e Responsabilidades dos Componentes

A tabela abaixo lista os arquivos principais do projeto e suas respectivas atribuições:

| Arquivo | Pacote | Responsabilidade Principal |
| :--- | :--- | :--- |
| [CatDetectorAnalyzer.kt](app/src/main/java/com/catwatch/detector/camera/CatDetectorAnalyzer.kt) | `camera` | Recebe os frames do CameraX, aplica descarte temporal (2 FPS), converte para `InputImage`, executa a inferência do ML Kit e notifica detecções felinas. |
| [CaptureCoordinator.kt](app/src/main/java/com/catwatch/detector/core/CaptureCoordinator.kt) | `core` | Gerencia a lógica de disparo, janela de confirmação de 15s ($T_0$ e $T_5$), cooldown de 120s e gravação das fotos no diretório privado do app. |
| [CatWatchService.kt](app/src/main/java/com/catwatch/detector/core/CatWatchService.kt) | `core` | Mantém o aplicativo em primeiro plano 24/7 com notificação contínua silenciosa para evitar morte do processo pelo sistema. |
| [StoragePurgeManager.kt](app/src/main/java/com/catwatch/detector/core/StoragePurgeManager.kt) | `core` | Executa rotinas de limpeza em background, removendo registros e fotos órfãs ou com mais de 30 dias. |
| [CatEventEntity.kt](app/src/main/java/com/catwatch/detector/data/CatEventEntity.kt) | `data` | Entidade Room representando os metadados de uma captura (`id`, `timestamp`, `filePath`, `confidence`, `eventType`, `isConfirmedDrinking`). |
| [CatEventDao.kt](app/src/main/java/com/catwatch/detector/data/CatEventDao.kt) | `data` | Interface DAO com consultas SQL por data, inserções e deleções atômicas individuais ou em lote. |
| [CatWatchDatabase.kt](app/src/main/java/com/catwatch/detector/data/CatWatchDatabase.kt) | `data` | Instância singleton do banco SQLite via Room. |
| [CatEventRepository.kt](app/src/main/java/com/catwatch/detector/data/CatEventRepository.kt) | `data` | Camada de repositório que abstrai as operações de banco via corrotinas. |
| [MainActivity.kt](app/src/main/java/com/catwatch/detector/ui/MainActivity.kt) | `ui` | Activity principal: vinculação do ciclo de vida da câmera, seletor de 3 estados, painel deslizante, permissões e integração com o ViewModel. |
| [CatEventViewModel.kt](app/src/main/java/com/catwatch/detector/ui/CatEventViewModel.kt) | `ui` | Gerencia o estado da interface (`StateFlow`), aplica filtros de data, coordena exclusões e expõe a lista formatada para o feed. |
| [FeedItem.kt](app/src/main/java/com/catwatch/detector/ui/model/FeedItem.kt) | `ui.model` | Modelo de dados polimórfico da lista (`DateHeader` para divisores de data e `SessionHeader` para cards de álbum). |
| [FeedItemMapper.kt](app/src/main/java/com/catwatch/detector/ui/FeedItemMapper.kt) | `ui` | Transforma a lista bruta de eventos na estrutura agrupada por dia e hora civil do dia. |
| [CatEventAdapter.kt](app/src/main/java/com/catwatch/detector/ui/CatEventAdapter.kt) | `ui` | Adaptador do RecyclerView com múltiplos tipos de view, suporte a seleção múltipla e decodificação assíncrona de miniaturas. |
| [CatEventDetailDialogFragment.kt](app/src/main/java/com/catwatch/detector/ui/CatEventDetailDialogFragment.kt) | `ui` | Diálogo modal em tela cheia com `ViewPager2`, exibindo o carrossel de fotos da sessão e botões de ação. |
| [ChainedSessionManager.kt](app/src/main/java/com/catwatch/detector/utils/ChainedSessionManager.kt) | `utils` | Agrupa capturas ocorridas em intervalos curtos ($\le 5\text{min}$) para navegação sequencial no visualizador. |
| [ImageUtils.kt](app/src/main/java/com/catwatch/detector/utils/ImageUtils.kt) | `utils` | Utilitário para decodificação segura de imagens com subamostragem e formato `RGB_565` para economia de memória RAM. |
| [activity_main.xml](app/src/main/res/layout/activity_main.xml) | `res/layout` | Layout da tela principal com `PreviewView`, barra de modos operacionais, painel deslizante e RecyclerView. |
| [app/build.gradle.kts](app/build.gradle.kts) | `build` | Configuração de compilação, versões de SDK, dependências e geração dos APKs. |

---

## 4. Invariantes Técnicas Obrigatórias

Ao realizar qualquer alteração no código, as seguintes regras devem ser rigorosamente preservadas:

1. **Zero Gravação de Vídeo:** O aplicativo opera exclusivamente com `ImageAnalysis` (análise na memória) e `ImageCapture` (fotos pontuais). Não adicione bibliotecas de gravação de vídeo contínua.
2. **Buffer Safety Mandatória:** Toda chamada ao `ImageProxy` recebida no analisador da câmera deve chamar obrigatoriamente `imageProxy.close()` no listener de finalização, garantindo que o buffer nunca vaze.
3. **Respeito ao Hardware:** A taxa de inferência de IA não deve ultrapassar 2 FPS. Nenhuma inferência ou operação de I/O em disco pode ser executada na thread principal (*MainThread*).
4. **Sem Emojis na Interface:** Textos de botões, notificações, badges e diálogos não devem conter emojis. Utilize texto limpo acompanhado de ícones vetoriais nativos do Material Design quando necessário.
5. **Compatibilidade com Seletores:** Não utilize `SwitchCompat` ou `MaterialSwitch` devido a incompatibilidades conhecidas com a camada de acessibilidade de certas fabricantes. Para seletores, utilize sempre `MaterialButtonToggleGroup`.
6. **Superfície da Câmera Estática:** Nunca redimensione ou reconfigure dinamicamente a `PreviewView` durante gestos de rolagem da tela. O painel de feed deve utilizar translação vertical (`translationY`) para sobrepor a câmera sem causar travamentos na GPU.
7. **Respeito ao Estado Desligado:** Quando o modo persistido for `Desligado`, a câmera não deve ser inicializada nem vinculada ao ciclo de vida no arranque da aplicação.
