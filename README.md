💉 Vacina App - Gestão Vacinal & Visitas ACS
Aplicativo Android para profissionais de saúde, focado no controle de ciclos vacinais e organização da rotina de visitas domiciliares (ACS).

✨ Principais Funções
✅ Registro de Aplicação: Cadastro detalhado (lote, lab, unidade) com validação de status.

📅 Agenda de Vacinação: Visualização e agendamento inteligente da próxima dose baseado no calendário brasileiro.

🏠 Visitas ACS: Registro e notificações de visitas domiciliares para acompanhamento de pacientes.

🔄 Sincronização Híbrida: Funcionamento offline com persistência local e sincronização automática com Firebase.

🛠️ Status Dinâmico: Controle entre doses Pendentes, Agendadas, Aplicadas ou Canceladas.

🛠️ Tecnologias Utilizadas
Linguagem: Kotlin (Coroutines & LifecycleScope).

Banco de Dados: Room (Local) e Firebase Realtime Database (Nuvem).

Notificações: Alertas configurados para lembretes de vacinas e visitas agendadas.

🚀 Configuração Necessária
Para que a sincronização em nuvem e as notificações funcionem, é obrigatório:

Criar um projeto no Firebase Console.

Adicionar um app Android ao projeto com o pacote com.vacinas.vacina.

Baixar o arquivo google-services.json.

Colar o arquivo na pasta: app/ (diretório raiz do módulo do aplicativo).

📂 Estrutura de Código
FormularioVacinaActivity: Interface de registro e lógica de agendamento imediato.

VaccineScheduler: Motor de regras para sequência de doses (ex: Penta -> DTP).

FirebaseSyncManager: Gerenciador de integridade e sincronismo de dados.





<img width="200" height="600" alt="Screenshot_20260111_120051" src="https://github.com/user-attachments/assets/3fdeaa3a-f7fb-4e3c-bcf7-9776812c84e3" />
<img width="200" height="600" alt="Screenshot_20260111_120339" src="https://github.com/user-attachments/assets/0ef812e0-8fef-4e67-922a-33d930a3a6a9" />
<img width="200" height="600" alt="Screenshot_20260111_120522" src="https://github.com/user-attachments/assets/c279adfb-c4d9-46fd-95e5-351e5147c731" />
<img width="200" height="600" alt="Screenshot_20260111_120709" src="https://github.com/user-attachments/assets/dbc4cb42-b42f-4370-ad5f-dfd64a079113" />
