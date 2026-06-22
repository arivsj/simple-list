
***

````md
# Guia de Arquitetura e Padrões  
## Agentes de IA — Desenvolvedores Android Sênior

> Este documento define **regras obrigatórias de arquitetura, estrutura de código, fluxo de dados e práticas de desenvolvimento**.  
>  
> Qualquer agente de IA atuando neste projeto **DEVE** se comportar como um **desenvolvedor Android sênior**, seguindo rigorosamente este guia.  
>  
> Decisões fora deste padrão são consideradas *architectural drift*.

---

## 1. Visão Geral da Arquitetura

O projeto adota **Arquitetura Limpa (Clean Architecture)**, estruturada em **três camadas principais**:

- **Data**
- **Domain**
- **Presentation (UI)**

### Princípios Obrigatórios

- Separação clara de responsabilidades  
- Independência de frameworks  
- Alta testabilidade  
- Evolução previsível do código  
- Fluxo de dependências **sempre para dentro**  

### Responsabilidade das Camadas

#### Data
- Obtém e persiste dados
- Não contém lógica de negócio
- Conhece **APIs, banco local, SharedPreferences**
- Nunca conhece UI ou casos de uso

#### Domain
- Núcleo do sistema
- Contém **casos de uso**
- Define **interfaces de repositório**
- Não depende de Android, Retrofit, Room ou Compose

#### Presentation (UI)
- Exibe dados
- Recebe eventos do usuário
- Usa **MVVM + Jetpack Compose**
- Não acessa Data ou repositórios diretamente

---

## 2. Injeção de Dependência com Hilt

O projeto utiliza **Hilt** como padrão obrigatório de injeção de dependência.

### Princípios

- Nenhuma dependência concreta é criada manualmente
- Construtores devem ser preferidos
- Interfaces sempre que possível

### Anotações Obrigatórias

- `@HiltAndroidApp`  
  → Configurada na classe `Application`

- `@AndroidEntryPoint`  
  → Activities, Fragments e Composables root

- `@HiltViewModel`  
  → Todos os ViewModels

- `@Inject`  
  → Construtores de classes injetáveis

### Módulos Hilt

Os módulos devem ensinar **explicitamente** ao Hilt como prover dependências:

- `DataSourceModule.kt`
- `RepositoryModule.kt`
- `UseCaseModule.kt`

Nenhuma dependência “implícita” é permitida.

---

## 3. Estrutura Oficial de Pastas

A estrutura reflete exatamente a Arquitetura Limpa.  
Agentes NÃO devem criar pacotes paralelos ou alternativos.

```txt
app/src/main/java/br/gov/caixa/bolsafamilia/
├── data/
│   ├── dataSource/
│   ├── repository/
│   └── network/
├── domain/
│   └── useCase/
├── ui/
│   ├── feature/
│   ├── components/
│   ├── theme/
│   └── uiModel/
├── di/
├── navigation/
├── viewModel/
├── models/
└── util/
````

***

## 4. Responsabilidade de Cada Pacote

### `data/dataSource`

*   Comunicação direta com:
    *   APIs
    *   SharedPreferences
    *   Cache
*   Implementações concretas
*   Exemplo:
    *   `PushNotificationDataSourceImpl`
    *   `TokenPreferencesDataSource`

### `data/repository`

*   Implementa repositórios definidos no Domain
*   Orquestra múltiplos DataSources
*   Converte dados quando necessário

### `domain/useCase`

*   Contém **EXATAMENTE UMA RESPONSABILIDADE**
*   Nenhuma lógica de UI
*   Nenhum detalhe de rede ou persistência
*   Exemplo:
    *   `GetSocialCardUseCase`

### `ui/feature`

*   Uma feature = um subpacote
*   Contém:
    *   Composables da tela
    *   Estado da tela
    *   Eventos da UI

### `ui/components`

*   Componentes reutilizáveis
*   Sem lógica de negócio

### `viewModel`

*   Orquestra UI ↔ UseCases
*   Gerencia estado
*   Sobrevive a mudanças de configuração
*   Usa `StateFlow`

***

## 5. Fluxo Oficial de Dados (Unidirecional)

Este fluxo **NÃO PODE SER VIOLADO**:

```txt
UI → ViewModel → UseCase → Repository → DataSource
                                   ↓
UI ← ViewModel ← UseCase ← Repository ← DataSource
```

### Detalhamento do Fluxo

1.  **UI (Composable)**
    *   Observa `StateFlow`
    *   Emite eventos

2.  **ViewModel**
    *   Processa eventos
    *   Chama UseCases
    *   Expõe estado (Loading, Success, Error)

3.  **UseCase**
    *   Aplica regra de negócio
    *   Usa repositórios

4.  **Repository**
    *   Decide origem dos dados
    *   Combina múltiplos DataSources

5.  **DataSource**
    *   Executa operações externas

***

## 6. Criação de Nova Funcionalidade (CHECKLIST OBRIGATÓRIO)

Exemplo: **Extrato Detalhado**

### ✅ Passos Obrigatórios

1.  **Modelos**
    *   Criar DTOs em `models` se necessário

2.  **DataSource**
    *   Definir endpoint no Retrofit
    *   Implementar `dataSource`

3.  **Repositório**
    *   Interface no Domain
    *   Implementação em Data

4.  **UseCase**
    *   Um caso de uso por ação
    *   Nome expressivo:
        *   `GetDetailedExtractUseCase`

5.  **ViewModel**
    *   Injeta UseCase
    *   Expõe `StateFlow`

6.  **UI**
    *   Criar feature:
            ui/feature/detailedExtract
    *   Usar `hiltViewModel()`
    *   Observar estado

7.  **Injeção de Dependência**
    *   Registrar em módulos Hilt

8.  **Navegação**
    *   Declarar rota em `navigation`

***

## 7. Diretrizes para Agentes de IA

Qualquer agente de IA atuando neste projeto **DEVE**:

*   Pensar como um **Android Engineer Sênior**
*   Priorizar legibilidade e previsibilidade
*   Nunca violar o fluxo de dependência
*   Nunca acessar Data diretamente da UI
*   Nunca criar “atalhos arquiteturais”
*   Preferir **clareza à otimização prematura**

***

## 8. Objetivo Final

Este guia existe para garantir que:

*   Humanos e agentes de IA trabalhem com o mesmo modelo mental
*   O projeto seja:
    *   Escalável
    *   Testável
    *   Manutenível
    *   Evolutivo

Qualquer código gerado **fora deste padrão é considerado incorreto**.

***


