---
name: netbeans-bridge-rule
always_on: true
description: "Diretriz obrigatória de edição segura via NetBeans Bridge e protocolo de preview HTML interativo para telas."
---

# Diretriz de Edição e Criação de Telas via NetBeans Bridge

## 1. Edição em Memória (Preservação de Encoding e Histórico Local)
Quando o usuário solicitar edições em arquivos de código do projeto (Java, JPosto, etc.), priorize aplicar as alterações através da ponte do NetBeans (`netbeans-bridge` ou ferramentas `nb_edit_buffer` / `nb_replace_lines` / `nb_set_content`) em vez de sobrescrever diretamente no disco.
- **Zero corrupção de encoding**: O NetBeans manipula em memória e salva no charset nativo do projeto (ISO-8859-1 / Windows-1252 / UTF-8) quando o usuário pressiona `Ctrl+S`.
- **Histórico Local e Undo**: As edições são registradas como modificações de buffer com aba marcada como não salva (`*`), permitindo revisão visual e `Ctrl+Z`.

## 2. Protocolo Obrigatório de Telas (Preview HTML com Modo de Revisão)
Sempre que for solicitado planejar, criar ou reformular telas de interface gráfica (Swing, JPosto, Mint):
1. **NUNCA** crie ou altere arquivos de formulários Swing (`.form`/`.java`) ou execute `mint_gerar_tela` sem aprovação visual prévia do usuário.
2. **SEMPRE** gere um preview visual interativo em HTML (`preview_<nome_tela>.html`) no diretório de artefatos da conversa (`<appDataDir>/brain/<conversation-id>/`) usando como base o template `~/bridge/templates/template_preview_swing.html`.
3. O HTML deve incluir o motor interativo de comentários (`modo de revisão`), permitindo ao desenvolvedor clicar em qualquer botão, campo ou painel para anotar ajustes e copiar a lista consolidada em Markdown com 1 clique para colar no chat.
4. Apresente o link do preview no `implementation_plan.md` e aguarde os comentários ou aprovação expressa do usuário antes de iniciar a criação física via bridge.
