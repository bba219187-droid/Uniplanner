# UniPlanner

App Android para estudantes universitários (licenciatura, mestrado, doutoramento) organizarem cadeiras, testes, trabalhos, estudo e ginásio.

## O que faz (versão 0.1)

- **Cadeiras:** nome, professor, ECTS e cor.
- **Prazos:** testes e trabalhos com data, peso na nota e contagem decrescente. Lembretes 7 dias, 3 dias, 1 dia e 2 horas antes.
- **Estudo:** cronómetro por cadeira e registo manual de tempo, com o total da semana.
- **Ginásio:** planear treinos e marcá-los como feitos.
- **Semana:** o que há esta semana e objetivos de horas de estudo por cadeira. Todas as segundas às 8h chega uma notificação com o plano da semana.
- Português e inglês (segue a língua do telemóvel).

Os dados ficam guardados no telemóvel. A conta online, os grupos entre estudantes e a integração com o Moodle vêm nas próximas versões (ver o plano do projeto).

## Instalar no telemóvel

Cada push para `main` é compilado no GitHub Actions. Abre o separador **Actions**, escolhe a última execução verde e descarrega o artefacto `uniplanner-debug-apk`. No telemóvel, abre o `.apk` e permite a instalação de fontes desconhecidas.

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
- `ui/`: ecrãs em Jetpack Compose
