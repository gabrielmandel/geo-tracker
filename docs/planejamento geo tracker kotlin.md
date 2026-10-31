Segue o planejamento em nível de títulos, com o raciocínio das decisões principais.

Visão geral do fluxo

CSV do INMET → API de importação → banco SQL → API de consulta → Angular. O cálculo do R roda sobre os dados já no banco e grava cada execução relacionada à estação.

Decisões de arquitetura

SQL vs NoSQL: SQL, e concordo com o seu raciocínio.

O domínio é relacional: usuários e papéis, estações, importações, cálculos e resultados ligados por chave, com integridade e transações.
As consultas são sempre por estação, intervalo de datas e agregação (somar por mês ou ano, janelas para achar eventos de chuva), e SQL faz isso nativamente.
O volume cabe bem. 26 anos horários dão cerca de 228 mil linhas por estação por variável, então mesmo centenas de estações ficam em dezenas de milhões de linhas. Isso é tranquilo para PostgreSQL com índice por (estação, variável, instante) e, se precisar, particionamento por ano.
Sugiro PostgreSQL: carga em massa nativa, JSONB para os parâmetros dos cálculos e bom suporte em Kotlin/Spring. SQL Server também funcionaria, se preferir.
Um banco de séries temporais só valeria mais tarde, e o TimescaleDB é uma extensão do próprio PostgreSQL, sem trocar o modelo.

Onde ficam os valores (sua ideia da tabela separada). Sim, observações separadas de estações. Há duas formas:

Formato largo, uma coluna por variável do CSV. É simples e rápido, mas cada variável nova exige migração e fica amarrada ao layout do INMET.
Formato longo, com um catálogo de variáveis (código, nome, unidade, coluna de origem no CSV) e uma tabela de observações (estação, variável, instante em UTC, valor, indicador de qualidade, lote de importação).

Recomendo o formato longo, já que você prevê outros dados. O custo é ter mais linhas, porque cada linha horária do INMET tem cerca de 19 variáveis. Por isso, no MVP, importe só as variáveis habilitadas no catálogo (precipitação) e guarde o arquivo original para reimportar outras depois, sem baixar de novo. Ausentes viram nulo, sem códigos sentinela.

API em Kotlin. Sugiro Spring Boot, que casa direto com controller, service e repository. A alternativa mais enxuta é o Ktor, mas você montaria mais coisa à mão.

O controller cuida do contrato HTTP (DTOs, validação, paginação, erros), sem regra de negócio.
O service concentra as regras e as transações.
O repository só persiste e consulta.

Dois pontos de atenção: a carga das observações deve usar gravação em lote (salvar linha a linha com JPA seria lento demais), e o motor de cálculo do R deve ser uma classe pura, sem HTTP nem banco, para testar isoladamente.

Módulo	Controller	Service	Repository
Auth	AuthController	AuthService, JwtService	UsuarioRepository, RefreshTokenRepository
Importação	ImportacaoController	ImportacaoService (+ parser de CSV)	LoteImportacaoRepository, EstacaoRepository, ObservacaoRepository (carga em lote)
Consulta	EstacaoController, ObservacaoController	EstacaoService, ObservacaoService	EstacaoRepository, ObservacaoRepository (consultas e agregações)
Cálculo do R	CalculoController	CalculoService (+ motor de cálculo puro)	CalculoRepository, EventoRepository, ResultadoRepository

Fluxo de importação do CSV

Upload e validação (extensão, tamanho, nome, cabeçalho). Registra um lote de importação, com status e hash do arquivo para evitar duplicidade.
Leitura em streaming, sem carregar o arquivo inteiro: metadados da estação, cabeçalho e linhas.
Cria ou atualiza a estação pelos metadados (código, nome, UF, região, coordenadas, altitude).
Normaliza: vírgula decimal, horário UTC, ausentes em nulo, colunas mapeadas para variáveis do catálogo.
Grava em blocos, de forma idempotente por (estação, variável, instante), então reimportar o mesmo arquivo não duplica.
Fecha o lote com contagens (lidas, gravadas, rejeitadas), relatório de erros e status consultável por GET.

Os CSVs variam de formato entre anos, então o parser precisa ser tolerante e testado com arquivos reais de vários anos. Para os ZIPs anuais, com centenas de CSVs, o upload HTTP é pouco prático. No MVP aceite um CSV por estação. Na fase 2, processe o ZIP em segundo plano ou por um comando de linha.

Cálculo do R

Ponto de partida: o pipeline Python atual é a especificação. A primeira tarefa é mapear os módulos dele em estágios e extrair as premissas (definição de evento erosivo, energia da chuva, intensidade máxima com dados horários, tratamento de falhas) para virarem parâmetros.
Entidade central: a execução de cálculo, com estação, período, método e versão, parâmetros (snapshot em JSON), status, usuário e resumo (R final, anos usados, % de falhas).
Não há restrição única de estação e período, então você pode ter vários cálculos por estação, com datas ou parâmetros diferentes.
Mudar parâmetros gera uma nova execução e nunca sobrescreve a anterior, o que mantém tudo reproduzível.
Filhas da execução: eventos erosivos (intermediários, para auditoria e gráficos) e resultados por mês e ano.
Configurável e extensível: cada método declara os parâmetros que aceita (com padrões) e a variável de entrada de que precisa, hoje a precipitação. Presets, como "padrão do pipeline atual", evitam reconfigurar tudo. Outras variáveis e índices entram depois só declarando outras variáveis do catálogo, sem mudar a API.
Estágios (a confirmar com o pipeline atual): seleção do período e validação de cobertura, eventos, energia e intensidade, agregação mensal e anual, R final, persistência. Cada estágio é um componente pequeno e testável.
Onde calcular:
(A) No Kotlin, lendo as observações da estação em ordem, em streaming. Os eventos de chuva são sequenciais e uma estação-período cabe em memória.
(B) Em SQL com window functions. É rápido, mas difícil de manter e testar.
(C) Manter o Python como worker. Reaproveita o código validado, mas cria dois runtimes.
Recomendo (A), usando as saídas do Python como testes de regressão (mesmas estações e anos, mesmo R). O (C) fica como plano B se o port for caro.
Execução: persista o status desde o início (pendente, executando, concluído, falhou). Como uma estação com 26 anos tem cerca de 228 mil linhas, o MVP pode rodar de forma síncrona. Segundo plano e lote por várias estações ficam para depois, sem mudar o modelo.
Saídas: manter a exportação em CSV e GeoJSON do pipeline atual (para o TerrSet), agora a partir dos resultados salvos.

Auth (JWT). Spring Security stateless. O login devolve um access token de curta duração e um refresh token guardado no banco e revogável. Os papéis seriam, por exemplo, ADMIN (importa dados e gerencia usuários) e USER (consulta e roda cálculos). Senhas com BCrypt ou Argon2, e cada cálculo pertence a um usuário.

Épico: Plataforma de estações INMET com cálculo do fator R

F0 · Fundação do projeto

Definir stack e versões (Kotlin, Spring Boot, PostgreSQL)
Estrutura de pacotes por módulo e camadas
Docker Compose (API + banco) para desenvolvimento
Migrations com Flyway e schema inicial
Perfis de configuração, logs e health check
Padrão de erros e documentação OpenAPI
Base de testes (unitários e integração com Testcontainers)

F1 · Modelo de dados

Usuários, papéis e refresh tokens
Estações
Catálogo de variáveis (coluna do CSV → variável)
Observações (formato longo, UTC, qualidade)
Lotes de importação
Índices e particionamento por ano
Execuções de cálculo, eventos e resultados

F2 · Autenticação e usuários (JWT)

Cadastro e gestão de usuários
Login e emissão de tokens
Refresh e revogação
Configuração de segurança e regras por papel
CORS e integração com o Angular (interceptor, guard, tela de login)
Testes de acesso por endpoint

F3 · Importação de CSV

Endpoint de upload (CSV por estação)
Validação do arquivo
Parser em streaming
Normalização (UTC, decimal, ausentes)
Criação ou atualização da estação pelos metadados
Gravação em lote idempotente
Relatório do lote e consulta de status
Teste com arquivos reais de anos diferentes
Fase 2: ZIP anual em segundo plano

F4 · Consulta de dados

Listar e filtrar estações
Detalhe da estação e cobertura de dados
Observações por intervalo (paginação e agregação por hora, dia e mês)
Catálogo de variáveis
Histórico de importações

F5 · Motor de cálculo do R

Levantar o método do pipeline Python (módulos, premissas, parâmetros)
Definir parâmetros configuráveis e presets
Definir o contrato do método (variáveis de entrada, parâmetros, saída)
Implementar os estágios: cobertura, eventos, energia e intensidade, agregação, R
Regras de qualidade (cobertura mínima, anos válidos)
Testes de regressão contra a saída do Python

F6 · API de cálculos

Criar execução com status
Listar por estação e por usuário
Detalhe com eventos e resultados por período
Duplicar ou reexecutar com novos parâmetros
Exportar CSV e GeoJSON

F7 · Integração com o Angular

Gerar o cliente a partir do OpenAPI
Telas: login, estações, dados, cálculos
Estados de execução e tratamento de erros

Backlog pós-MVP

Atualização periódica dos dados
Novas variáveis e novos métodos
Execução em lote ou por fila
Hospedagem e CI/CD
Otimizações (cache, TimescaleDB)
Ordem, critério de pronto e decisões

Ordem sugerida: F0 → F1 → F2 → F3 → F4 → F5 → F6, com o F7 acompanhando. O Auth vem antes dos endpoints de dados para que todos já nasçam protegidos, o que é mais barato do que encaixar segurança depois.

Critério de pronto do MVP: importar os 26 anos de uma estação e reproduzir, pela API, o mesmo R que o pipeline Python gera para ela, com o resultado salvo e consultável no Angular.

Decisões para confirmar (assumi os padrões entre parênteses):

Spring Boot + PostgreSQL.
Estações e anos do MVP (só as da pesquisa, para validar antes de escalar).
Cálculos privados por usuário ou compartilhados.
Token no Angular (access token em memória com refresh em cookie httpOnly é o mais seguro; localStorage é o mais simples).
Hospedagem (local com Docker Compose primeiro).