# Design: Definição de Papéis de Agente (Solutions Architect, Technical Writer, Java Full-stack Engineer, DevOps/Platform Engineer)

## Contexto

O repositório `planet-ImportExport` é um projeto Java 25/Gradle/Quarkus recém-iniciado, com OpenSpec já configurado em `docs/openspec/`. O `AGENTS.md` atual descreve um único "papel guarda-chuva" (dev full-stack + writer + DevOps + arquiteto). O usuário já possui, em `~/.claude/agents/`, um conjunto de agentes "Plinth Toolkit" (`plinth-architect`, `plinth-business-analyst`, `plinth-tech-lead`, `plinth-java-*-coder`, etc.) que seguem um padrão consistente de frontmatter e estrutura, e referenciam uma malha de skills numeradas (`@0NN-...`) instaladas separadamente.

Este trabalho define 4 novos papéis de agente — **Solutions Architect**, **Technical Writer**, **Java Full-stack Engineer**, **DevOps/Platform Engineer** — como agentes de nível usuário (`~/.claude/agents/*.md`), disponíveis em qualquer repositório, com conteúdo próprio e autocontido (sem depender das skills `@0NN` do Plinth Toolkit, embora possam citá-las como referência de mercado). O `AGENTS.md` do repositório passa a indexar esses papéis.

## Objetivo

Cada agente deve:

- Ter responsabilidades claras e fronteiras explícitas com os outros 3 papéis (evitar sobreposição não intencional).
- Fundamentar suas decisões citando, inline, a norma/framework/prática que a sustenta: ISO/IEC 27000 (em especial 27001/27002), TOGAF, PRINCE2, ITIL 4, Scrum Guide, CompTIA, quando aplicável ao papel.
- Seguir, quando aplicável, os padrões técnicos: Design Patterns (GoF), SOLID, Clean Code, Clean Architecture, Object Calisthenics, TDD, BDD, ATDD, DDD, Data Modelling, ACID.
- Ser rastreável a referências acadêmicas/de mercado com boa reputação (SWEBOK, livros O'Reilly/Pearson/IT Revolution Press, guias oficiais TOGAF/ITIL/Scrum.org, dora.dev).

## Fora de escopo

- Não substitui nem depende dos agentes `plinth-*` já existentes.
- Não cria novas skills numeradas nem edita o Plinth Toolkit.
- Não define pipelines de CI/CD reais nem infraestrutura — os agentes são prompts de orientação, não automações executáveis.

## Mapeamento normativo por papel

### 1. Solutions Architect

**Responsabilidades centrais** (SWEBOK v4 – Knowledge Area "Software Architecture"; iSAQB CPSA Foundation): esclarecer requisitos/restrições, projetar e decidir a arquitetura, comunicar e documentar decisões, avaliar arquiteturas existentes.

**Normas aplicáveis:**

- TOGAF ADM — Fases B (Business Architecture), C (Information Systems: Data & Application Architecture), D (Technology Architecture) — decomposição e alinhamento com negócio.
- ISO/IEC 27001 Anexo A.5 (Organizational Controls) e A.8 (Technological Controls) — decisões arquiteturais de segurança (segmentação, criptografia, gestão de acesso) desde o design.
- ITIL 4 — prática "Information Security Management" — privacy/security by design.
- PRINCE2 (7ª ed.) — tema "Business Case" — viabilidade técnica como insumo ao caso de negócio.

**Padrões técnicos:** DDD estratégico (bounded contexts, context mapping, linguagem ubíqua), Clean Architecture (Dependency Rule), SOLID (Dependency Inversion Principle), Design Patterns (GoF) como vocabulário estrutural, Data Modelling e ACID nas decisões de persistência/consistência.

**Referências:** SWEBOK Guide v4.0 (IEEE); iSAQB CPSA Foundation Curriculum; *Clean Architecture* (Robert C. Martin, Pearson 2017); *Implementing Domain-Driven Design* (Vaughn Vernon, Addison-Wesley 2013); The TOGAF Standard (The Open Group).

### 2. Technical Writer

**Responsabilidades centrais** (STC Technical Communication Body of Knowledge — TCBOK): criar e manter documentação técnica clara e coerente, ponte entre especialistas e usuários; arquitetura de informação, estratégia de conteúdo, usabilidade.

**Normas aplicáveis:**

- ITIL 4 — Service Value Chain / prática "Service Design" — documentação como insumo formal de transição de serviço.
- ISO/IEC 27001 Anexo A.5 — documentação de políticas/procedimentos exigida por um SGSI.
- PRINCE2 — componente "Organização" / "Product Descriptions" — disciplina de controle documental.
- Scrum Guide — Definition of Done frequentemente inclui documentação como critério de "incremento utilizável".

**Padrões técnicos:** DDD (linguagem ubíqua aplicada à documentação), BDD (Gherkin como documentação executável), Clean Code (documentar o "porquê", não repetir o que o código já expressa), ADRs como formato padrão de decisão documentada.

**Referências:** *Docs for Developers* (Jared Bhatti et al., Apress 2021); *Every Page is Page One* (Mark Baker, XML Press 2013); STC TCBOK; *Docs Like Code* (Anne Gentle); Scrum Guide 2020 (Definition of Done).

### 3. Java Full-stack Engineer

**Responsabilidades centrais** (SWEBOK — Design, Construction, Testing, Software Engineering Operations): projetar, construir, testar e operar software; nível tático de implementação (em contraste com o nível estratégico do Solutions Architect).

**Normas aplicáveis:**

- Scrum Guide 2020 — accountability "Developers" — enquadramento normativo mais direto entre os frameworks listados.
- ISO/IEC 27001 Anexo A.8 (Technological Controls) — implementação de controles técnicos concretos (auth, criptografia, logging seguro, secrets).
- ITIL 4 — prática "Software Development and Management".
- CompTIA Security+ — papel-alvo explícito "DevOps/Software Developer".

**Padrões técnicos:** SOLID, Clean Code, Object Calisthenics (as 9 regras de Jeff Bay), Design Patterns (GoF), TDD/BDD/ATDD (ciclo completo de testes), DDD tático (entidades, agregados, value objects, repositórios), ACID, Clean Architecture (implementação das camadas e Dependency Rule).

**Referências:** *Clean Code* (Robert C. Martin, Prentice Hall 2008); *Implementing Domain-Driven Design* (Vaughn Vernon, 2013); *The ThoughtWorks Anthology* (cap. Object Calisthenics, Jeff Bay); *Test-Driven Development: By Example* (Kent Beck, 2002); The 2020 Scrum Guide.

### 4. DevOps/Platform Engineer

**Responsabilidades centrais** (Google SRE Book): disponibilidade, latência, performance, eficiência, gestão de mudanças, monitoramento, resposta a incidentes, planejamento de capacidade; conceitos de error budget e toil.

**Normas aplicáveis:**

- ITIL 4 — Service Value Chain / "Deliver and Support" e prática "Information Security Management".
- ISO/IEC 27001 Anexo A.8 — executor primário de controles tecnológicos operacionais (config segura, monitoramento, backup, segmentação, gestão de vulnerabilidades).
- CompTIA Cloud+ (primeira certificação CompTIA com domínio DevOps) e Security+.

**Padrões técnicos:** ACID vs. consistência distribuída/eventual (trade-offs em bancos distribuídos, cache, mensageria), SOLID/Clean Code aplicados a Infrastructure as Code, TDD/BDD aplicados a testes de infraestrutura/pipeline, DDD (limites de deployment espelhando bounded contexts).

**Referências e métricas DORA:**

- *Site Reliability Engineering* e *The Site Reliability Workbook* (Google/O'Reilly).
- *Accelerate: The Science of Lean Software and DevOps* (Forsgren, Humble, Kim, IT Revolution Press 2018) — base empírica das métricas DORA.
- *The DevOps Handbook* (Kim, Humble, Debois, Willis) — CALMS e as Três Vias.
- **DORA Accelerate State of DevOps Report** (dora.dev, Google Cloud) — as 4 métricas clássicas (Deployment Frequency, Lead Time for Changes, Change Failure Rate, MTTR) mais a 5ª métrica **Reliability** (adicionada em 2021); catálogo de ~24 capacidades preditoras de alta performance (dora.dev/capabilities) incluindo trunk-based development, loosely coupled architecture, IaC, test automation, monitoring/observability, cultura organizacional generativa (Westrum); achados 2023-2025 sobre platform engineering/user-centricity e sobre o impacto misto da IA generativa (ganho de produtividade individual vs. queda de throughput/estabilidade quando mal aplicada).
- CompTIA Cloud+ Certification Exam Objectives.

## Estrutura de cada arquivo de agente

Local: `~/.claude/agents/<nome>.md` (nível usuário). Frontmatter no mesmo formato dos `plinth-*` (`name`, `description`, `license`, `metadata.author`/`version`, `model: inherit`), mas sem referenciar skills `@0NN`. Corpo com 5 seções:

1. **Core role** — 3-5 bullets do que o papel faz/não faz e fronteiras com os outros 3 papéis.
2. **Foundational references** — 4-6 fontes (livros/guias oficiais) que fundamentam o papel.
3. **Missions** — responsabilidades numeradas; cada item cita inline a norma/prática que a sustenta (formato: "Aplicar X (Norma, seção/conceito)").
4. **Constraints / Safeguards** — o que o agente nunca faz; handoff explícito para os outros 3 papéis.
5. **Output format** — seções de saída padronizadas para relatórios/entregas do agente.

Nomes de arquivo propostos:

- `solutions-architect.md`
- `technical-writer.md`
- `java-fullstack-engineer.md`
- `devops-platform-engineer.md`

## Atualização do AGENTS.md do repositório

Adicionar seção "## Agent roles" ao `AGENTS.md` do `planet-ImportExport`, com uma tabela indexando os 4 papéis: nome do arquivo (em `~/.claude/agents/`), resumo de uma linha, e quando invocar cada um. Não duplica o conteúdo dos agentes — apenas referencia.

## Testes / validação

Não há testes automatizados aplicáveis (são arquivos de prompt/documentação). Validação:

- Revisão humana do conteúdo de cada agente (fronteiras, citações corretas).
- Checagem de que nenhuma citação normativa é inventada (seções/números não confirmados foram evitados na pesquisa; usar nome do conceito/fase quando o número exato não é público).
