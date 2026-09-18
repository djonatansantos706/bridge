# Antigravity NetBeans Bridge Suite — Instruções Mestres de Operação

Esta suíte de ferramentas conecta o assistente de IA diretamente ao editor em memória do Apache NetBeans.
Siga RIGOROSAMENTE os protocolos abaixo em qualquer projeto ou tarefa:

---

## 1. 🛡️ Edição Segura e Preservação de Histórico (In-Memory Buffer)

1. **PROIBIDO sobrescrever arquivos de código diretamente em disco via ferramentas nativas de filesystem** quando o NetBeans Bridge estiver disponível.
2. **Utilize SEMPRE as ferramentas de buffer do NetBeans Bridge**:
   - `nb_edit_buffer`: Para substituições de trechos de código com verificação de unicidade.
   - `nb_replace_lines`: Para substituição de intervalos específicos de linhas.
   - `nb_set_content`: Para substituição atômica de conteúdo completo de buffer.
3. **Preservação de Encoding:** O NetBeans manipula em memória e salva no charset nativo do projeto (`Windows-1252`, `ISO-8859-1`, `UTF-8`).
4. **Histórico Local e Undo (`Ctrl+Z`):** As edições marcam a aba como modificada (`*`), mantendo o desenvolvedor no controle total da revisão antes de salvar no disco (`nb_save_buffer` ou `Ctrl+S`).

---

## 2. 🎨 Protocolo Obrigatório de Telas (HTML Preview com Modo de Revisão)

> [!IMPORTANT]
> **NUNCA crie ou altere arquivos de formulários Swing (`.form`/`.java`) ou execute `mint_gerar_tela` sem aprovação visual prévia do desenvolvedor.**

Sempre que a tarefa envolver planejar, criar ou reformular telas de interface gráfica (Swing, JPosto, Mint):

### Fluxo Obrigatório de 4 Etapas:

1. **Gerar Preview HTML Interativo:**
   - Antes de tocar no código Java/Swing, gere um arquivo HTML de preview (ex: `preview_<nome_tela>.html`) no diretório de artefatos da conversa (`<appDataDir>/brain/<conversation-id>/`).
   - O HTML deve simular com fidelidade a janela Swing (barra de título, abas, campos de formulário, botões de ação e tabelas).
   - Use o template canônico disponível em `~/bridge/templates/template_preview_swing.html` como referência.

2. **Embutir o Motor de Revisão Interativo (Comentários por Componente):**
   - O preview HTML deve OBRIGATORIAMENTE conter o modo de revisão habilitado por padrão.
   - Cada componente (botões, inputs, combos, checkboxes, abas, tabelas) deve possuir o atributo `data-component="Nome Técnico / Label"`.
   - Ao clicar em qualquer elemento, abre-se um diálogo para o desenvolvedor anotar o que deseja mudar (ex: *"não gostei deste botão aqui, mover para o rodapé e trocar o texto"*).
   - O componente ganha um alfinete numerado (*pin*) indicando a anotação.
   - Deve conter o botão de 1 clique **"📋 Copiar Feedback para o Chat"**, que exporta a lista de comentários em Markdown estruturado para o desenvolvedor colar diretamente na conversa (`Ctrl+V`).

3. **Apresentar no Planejamento e Aguardar:**
   - Inclua o link direto do arquivo HTML no `implementation_plan.md`.
   - **PARE e aguarde** a interação do desenvolvedor. Se ele colar a lista de comentários, ajuste o layout até obter 100% de aprovação.

4. **Criação Fiel via Bridge / Mint:**
   - Somente após a aprovação expressa do layout, execute a criação dos arquivos `.form` e `.java` via NetBeans Bridge (`nb_form_create_blueprint` ou `mint_gerar_tela`).
