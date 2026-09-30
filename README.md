# UniPlanner

App Android para estudantes universitários (licenciatura, mestrado, doutoramento) organizarem cadeiras, testes, trabalhos, estudo e ginásio.

## O que faz

- **Cadeiras:** nome, professor, ECTS e cor.
- **Prazos:** testes e trabalhos com data, peso na nota e contagem decrescente. Lembretes 7 dias, 3 dias, 1 dia e 2 horas antes.
- **Estudo:** cronómetro por cadeira e registo manual de tempo, com o total da semana.
- **Ginásio:** planear treinos e marcá-los como feitos.
- **Semana:** o que há esta semana e objetivos de horas de estudo por cadeira. Todas as segundas às 8h chega uma notificação com o plano da semana.
- Português e inglês (segue a língua do telemóvel).

- **Moodle:** ligar ao Moodle da universidade, ver trabalhos e o estado de entrega, importar prazos e enviar ficheiros (rascunho ou submissão final). Requer que a universidade tenha o acesso móvel ao Moodle ativo.
- **Amigos e grupos:** conta com email ou Google, código de amigo, pedidos de amizade, grupos por turma com código de convite e partilha de apontamentos e links.
- **Atualizações:** a app avisa quando há uma versão nova e abre o download.

As cadeiras, prazos e horas de estudo ficam guardados no telemóvel. Os amigos e grupos usam o Firebase.

## Ligar o Firebase

1. Criar um projeto em console.firebase.google.com e adicionar uma app Android com o pacote `com.uniplanner.app` e o SHA-1 da chave de `app/debug.keystore`.
2. Pôr o `google-services.json` em `app/`. Sem este ficheiro a app compila e funciona, mas sem a parte online.
3. Ativar Authentication (Email/palavra-passe e Google) e criar a Firestore Database.
4. Copiar `firestore.rules` para Firestore Database > Regras e publicar.

## Instalar no telemóvel

Abrir no telemóvel: https://github.com/bba219187-droid/Uniplanner/releases/latest/download/UniPlanner.apk

Cada push é compilado no GitHub Actions, que publica o APK e um `version.json` na release `versao-teste`. A app compara o `versionCode` com o seu e avisa quando há versão nova.

## Compilar localmente

Requer o Android Studio (ou o Android SDK com JDK 17):

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Estrutura

- `data/`: base de dados local (Room)
- `domain/Planning.kt`: regras do plano semanal e dos lembretes (com testes)
- `reminders/`: notificações e tarefas em segundo plano (WorkManager)
- `moodle/`: cliente da API de web services do Moodle (com testes)
- `online/`: contas, amigos e grupos (Firebase Auth + Firestore)
- `update/`: aviso de versões novas
- `ui/`: ecrãs em Jetpack Compose
