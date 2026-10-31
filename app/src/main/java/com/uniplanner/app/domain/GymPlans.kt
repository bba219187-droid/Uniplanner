package com.uniplanner.app.domain

/** The muscles a drawing can light up; see the body figure on the gym screen. */
enum class Muscle { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, CORE, QUADS, HAMSTRINGS, GLUTES, CALVES, CARDIO }

/** How the plans are grouped in the list: whole weeks, one muscle group per session, or at home. */
enum class PlanKind(val label: String) { WEEK("Programas semanais"), MUSCLE("Treinos por músculo"), HOME("Em casa") }

data class PlanExercise(
    val name: String,
    val muscles: Set<Muscle>,
    val sets: Int,
    /** Reps for each set; the workout starts with this number. */
    val reps: Int,
    val restSec: Int,
    val how: String,
    /** Top of the rep range, 8 to 12 when [reps] is 8 and this is 12. */
    val repsMax: Int = reps,
    /** "" for reps, "s" for seconds, " min" for minutes. */
    val unit: String = "",
) {
    val repsText: String get() = (if (repsMax > reps) "$reps–$repsMax" else "$reps") + unit
    val timed: Boolean get() = unit.isNotEmpty()
}

const val DEFAULT_WARMUP = "5 min de bicicleta ou passadeira leve, rodar ombros e anca, e 2 séries leves do primeiro exercício."

data class PlanDay(
    val name: String,
    val emoji: String,
    val minutes: Int,
    val exercises: List<PlanExercise>,
    /** Muscles of the day in words, "Peito · Tríceps". */
    val focus: String = "",
    val warmup: String = DEFAULT_WARMUP,
)

data class GymPlan(
    val id: String,
    val name: String,
    val short: String,
    val level: String,
    val perWeek: Int,
    val goal: String,
    val about: String,
    /** Suggested weekdays, 1 = Monday, one per entry of [rotation]. */
    val weekdays: List<Int>,
    /** Which day of [days] goes on each suggested weekday. */
    val rotation: List<Int>,
    val days: List<PlanDay>,
    val colorArgb: Long,
    val kind: PlanKind = PlanKind.WEEK,
    /** How to keep progressing on this plan. */
    val tips: List<String> = emptyList(),
)

private fun ex(name: String, sets: Int, reps: Int, rest: Int, how: String, vararg m: Muscle) =
    PlanExercise(name, m.toSet(), sets, reps, rest, how)

/** 8 to 12 instead of 8. */
private fun PlanExercise.upTo(max: Int) = copy(repsMax = max)
private fun PlanExercise.sets(n: Int) = copy(sets = n)
private fun PlanExercise.secs() = copy(unit = "s")
private fun PlanExercise.mins() = copy(unit = " min")

private object Moves {
    // Pernas.
    val squat = ex("Agachamento", 4, 6, 150, "Pés à largura dos ombros, desce como se te sentasses numa cadeira até a anca ficar ao nível dos joelhos, joelhos na direção dos pés e costas direitas.", Muscle.QUADS, Muscle.GLUTES).upTo(8)
    val goblet = ex("Agachamento goblet", 3, 10, 75, "Segura um haltere junto ao peito, desce com o peito alto e sobe a empurrar o chão.", Muscle.QUADS, Muscle.GLUTES, Muscle.CORE).upTo(12)
    val deadlift = ex("Peso morto romeno", 3, 8, 120, "Joelhos ligeiramente fletidos, leva a anca atrás com a barra junto às pernas até sentires os posteriores esticar, e sobe apertando os glúteos.", Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.BACK).upTo(10)
    val legPress = ex("Leg press", 3, 10, 90, "Pés a meio da plataforma, desce até 90° nos joelhos e empurra sem trancar.", Muscle.QUADS, Muscle.GLUTES).upTo(12)
    val lunge = ex("Afundos", 3, 10, 75, "Passo largo à frente, desce até o joelho de trás quase tocar no chão. As repetições são por perna.", Muscle.QUADS, Muscle.GLUTES).upTo(12)
    val bulgarian = ex("Agachamento búlgaro", 3, 8, 90, "Pé de trás em cima do banco, desce a direito com o peso na perna da frente. Repetições por perna.", Muscle.QUADS, Muscle.GLUTES).upTo(10)
    val legExt = ex("Extensão de pernas", 3, 12, 60, "Na máquina, estica os joelhos até ao topo, segura 1 segundo e desce devagar.", Muscle.QUADS).upTo(15)
    val legCurl = ex("Curl de pernas", 3, 10, 60, "Deitado ou sentado na máquina, traz os calcanhares aos glúteos e volta em 3 segundos.", Muscle.HAMSTRINGS).upTo(12)
    val hipThrust = ex("Hip thrust", 4, 8, 90, "Costas apoiadas no banco, barra na anca, sobe até o corpo ficar reto e aperta os glúteos 1 segundo no topo.", Muscle.GLUTES, Muscle.HAMSTRINGS).upTo(12)
    val abductor = ex("Abdutora", 3, 15, 45, "Sentado na máquina, abre as pernas contra a resistência e volta devagar.", Muscle.GLUTES).upTo(20)
    val calves = ex("Gémeos em pé", 4, 12, 45, "Sobe na ponta dos pés o mais alto possível, segura 1 segundo e desce até esticar bem.", Muscle.CALVES).upTo(15)
    val seatedCalves = ex("Gémeos sentado", 3, 15, 45, "Joelhos a 90° debaixo do apoio, sobe e desce devagar em toda a amplitude.", Muscle.CALVES).upTo(20)

    // Peito.
    val bench = ex("Supino reto", 4, 6, 150, "Deitado no banco, omoplatas juntas e pés no chão, desce a barra até ao peito e empurra até esticar os braços.", Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS).upTo(8)
    val dbBench = ex("Supino com halteres", 3, 8, 90, "Halteres ao lado do peito, empurra para cima e junta-os no topo sem bater.", Muscle.CHEST, Muscle.TRICEPS).upTo(12)
    val incline = ex("Supino inclinado com halteres", 3, 8, 90, "Banco a 30°, desce devagar até ao peito e empurra para cima. Trabalha a parte de cima do peito.", Muscle.CHEST, Muscle.SHOULDERS).upTo(12)
    val cableFly = ex("Crossover na polia", 3, 12, 60, "Polias altas, braços ligeiramente fletidos, junta as mãos à frente do peito como num abraço e abre devagar.", Muscle.CHEST).upTo(15)
    val pecDeck = ex("Peck deck", 3, 12, 60, "Sentado na máquina, junta os braços à frente apertando o peito, volta sem deixar o peso bater.", Muscle.CHEST).upTo(15)
    val chestDips = ex("Paralelas (peito)", 3, 8, 90, "Corpo inclinado à frente, desce até os ombros ficarem abaixo dos cotovelos e sobe. Usa a máquina assistida se precisares.", Muscle.CHEST, Muscle.TRICEPS).upTo(12)

    // Costas.
    val row = ex("Remada com barra", 4, 8, 120, "Tronco inclinado e costas retas, puxa a barra até ao umbigo apertando as omoplatas.", Muscle.BACK, Muscle.BICEPS).upTo(10)
    val dbRow = ex("Remada unilateral", 3, 10, 75, "Mão e joelho no banco, puxa o haltere até à anca com o cotovelo junto ao corpo. Repetições por braço.", Muscle.BACK, Muscle.BICEPS).upTo(12)
    val pulldown = ex("Puxada na polia", 3, 10, 90, "Pega larga, puxa a barra até ao peito com o peito para fora e sobe devagar.", Muscle.BACK, Muscle.BICEPS).upTo(12)
    val pullup = ex("Elevações", 3, 6, 120, "Pendura-te com os braços esticados e puxa até o queixo passar a barra. Usa elástico ou a máquina assistida se precisares.", Muscle.BACK, Muscle.BICEPS).upTo(10)
    val seatedRow = ex("Remada sentado na polia", 3, 10, 75, "Peito alto, puxa o triângulo até à barriga, junta as omoplatas e estica os braços sem curvar as costas.", Muscle.BACK, Muscle.BICEPS).upTo(12)
    val pullover = ex("Pullover na polia", 3, 12, 60, "De pé, braços quase esticados, leva a barra da altura da cabeça até às coxas usando as costas.", Muscle.BACK).upTo(15)
    val facePull = ex("Face pull", 3, 15, 60, "Corda na polia alta, puxa até à cara abrindo os cotovelos. Faz bem aos ombros de quem estuda curvado.", Muscle.SHOULDERS, Muscle.BACK).upTo(20)
    val backExt = ex("Extensões lombares", 3, 12, 60, "No banco romano, desce o tronco com as costas direitas e sobe até ficar em linha com as pernas.", Muscle.BACK, Muscle.GLUTES).upTo(15)

    // Ombros.
    val ohp = ex("Press militar", 4, 6, 120, "De pé, barra à frente dos ombros, empurra por cima da cabeça sem arquear as costas.", Muscle.SHOULDERS, Muscle.TRICEPS).upTo(8)
    val dbOhp = ex("Press de ombros com halteres", 3, 8, 75, "Sentado, halteres à altura das orelhas, empurra para cima até quase se tocarem.", Muscle.SHOULDERS, Muscle.TRICEPS).upTo(12)
    val arnold = ex("Press Arnold", 3, 10, 75, "Começa com as palmas viradas para ti e roda-as para a frente enquanto empurras para cima.", Muscle.SHOULDERS, Muscle.TRICEPS).upTo(12)
    val lateral = ex("Elevações laterais", 4, 12, 45, "Braços ligeiramente fletidos, sobe os halteres até à altura dos ombros e desce devagar. Peso leve, sem balanço.", Muscle.SHOULDERS).upTo(20)
    val frontRaise = ex("Elevações frontais", 3, 12, 45, "Sobe o haltere à frente até à altura dos olhos e desce em 3 segundos.", Muscle.SHOULDERS).upTo(15)
    val rearDelt = ex("Aberturas invertidas", 3, 15, 45, "Tronco inclinado à frente, abre os braços para os lados apertando a parte de trás dos ombros.", Muscle.SHOULDERS, Muscle.BACK).upTo(20)
    val shrug = ex("Encolhimentos", 3, 12, 60, "Halteres ao lado do corpo, sobe os ombros em direção às orelhas, segura 1 segundo e desce.", Muscle.BACK).upTo(15)

    // Braços.
    val curl = ex("Curl de bíceps", 3, 10, 60, "Cotovelos colados ao corpo, sobe os halteres sem balançar o tronco e desce em 3 segundos.", Muscle.BICEPS).upTo(12)
    val barCurl = ex("Curl com barra", 3, 8, 75, "Pega à largura dos ombros, sobe a barra até ao peito com os cotovelos parados.", Muscle.BICEPS).upTo(10)
    val hammer = ex("Curl martelo", 3, 10, 60, "Palmas viradas uma para a outra, sobe e desce com controlo. Trabalha também o antebraço.", Muscle.BICEPS).upTo(12)
    val preacher = ex("Curl no banco Scott", 3, 10, 60, "Braços apoiados no banco inclinado, sobe sem tirar os cotovelos do apoio e desce até quase esticar.", Muscle.BICEPS).upTo(12)
    val inclineCurl = ex("Curl inclinado", 3, 10, 60, "Sentado num banco a 45°, braços pendurados atrás do corpo, sobe os halteres. Estica bem o bíceps.", Muscle.BICEPS).upTo(12)
    val pushdown = ex("Extensão de tríceps na polia", 3, 10, 60, "Cotovelos fixos junto ao corpo, empurra a corda para baixo até esticar e abre-a no fim.", Muscle.TRICEPS).upTo(15)
    val skull = ex("Testa com barra W", 3, 8, 75, "Deitado, braços esticados, dobra só os cotovelos para levar a barra até à testa e volta.", Muscle.TRICEPS).upTo(12)
    val overhead = ex("Extensão de tríceps acima da cabeça", 3, 10, 60, "Haltere ou corda atrás da cabeça, cotovelos apontados para cima, estica os braços.", Muscle.TRICEPS).upTo(12)
    val closeBench = ex("Supino pega fechada", 3, 8, 90, "Mãos à largura dos ombros, cotovelos junto ao corpo, desce a barra ao fundo do peito.", Muscle.TRICEPS, Muscle.CHEST).upTo(10)
    val dips = ex("Mergulhos no banco", 3, 10, 60, "Mãos no banco atrás de ti, desce dobrando os cotovelos até 90° e sobe.", Muscle.TRICEPS, Muscle.CHEST).upTo(15)

    // Abdómen.
    val plank = ex("Prancha", 3, 30, 45, "Antebraços no chão e corpo em linha reta, aperta os glúteos e o abdómen.", Muscle.CORE).upTo(60).secs()
    val crunch = ex("Abdominais crunch", 3, 15, 45, "Deitado, joelhos fletidos, sobe só os ombros do chão contraindo o abdómen.", Muscle.CORE).upTo(20)
    val legRaise = ex("Elevação de pernas suspenso", 3, 10, 60, "Pendurado na barra, sobe os joelhos ou as pernas esticadas sem balançar.", Muscle.CORE).upTo(15)
    val cableCrunch = ex("Crunch na polia", 3, 12, 60, "De joelhos, corda atrás da cabeça, enrola o tronco para baixo com o abdómen.", Muscle.CORE).upTo(15)
    val twist = ex("Russian twist", 3, 20, 45, "Sentado com os pés no ar, roda o tronco de um lado para o outro. Conta cada lado.", Muscle.CORE).upTo(30)
    val deadBug = ex("Dead bug", 3, 10, 45, "Deitado de costas, braços e pernas no ar, estica o braço e a perna opostos sem tirar as costas do chão. Por lado.", Muscle.CORE).upTo(12)

    // Corpo e cardio.
    val pushup = ex("Flexões", 3, 10, 60, "Mãos à largura dos ombros, corpo reto, desce o peito até quase ao chão. Joelhos no chão se precisares.", Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE).upTo(20)
    val pikePush = ex("Flexões pike", 3, 8, 60, "Anca no ar em V invertido, desce a cabeça até ao chão entre as mãos e empurra.", Muscle.SHOULDERS, Muscle.TRICEPS).upTo(12)
    val bwSquat = ex("Agachamento livre", 3, 20, 60, "Sem peso, desce devagar em 3 segundos e sobe com força.", Muscle.QUADS, Muscle.GLUTES).upTo(25)
    val glute = ex("Ponte de glúteos", 3, 15, 45, "Deitado de costas, pés no chão, sobe a anca e aperta os glúteos no topo.", Muscle.GLUTES, Muscle.HAMSTRINGS).upTo(20)
    val superman = ex("Superman", 3, 12, 45, "Deitado de barriga para baixo, levanta braços e pernas ao mesmo tempo e segura 2 segundos.", Muscle.BACK, Muscle.GLUTES)
    val towelRow = ex("Remada com toalha na porta", 3, 12, 60, "Toalha presa à maçaneta de uma porta fechada, inclina-te para trás e puxa o peito até à porta.", Muscle.BACK, Muscle.BICEPS).upTo(15)
    val burpee = ex("Burpees", 3, 10, 60, "Agacha, salta para prancha, faz uma flexão, volta e salta com os braços no ar.", Muscle.CARDIO, Muscle.CHEST, Muscle.QUADS).upTo(12)
    val climbers = ex("Mountain climbers", 3, 30, 45, "Em prancha, traz os joelhos ao peito alternadamente e depressa.", Muscle.CARDIO, Muscle.CORE).secs()
    val jacks = ex("Jumping jacks", 3, 40, 30, "Salta abrindo pernas e braços e volta a fechar.", Muscle.CARDIO).secs()
    val bike = ex("Bicicleta ou passadeira", 1, 20, 0, "Ritmo em que consegues falar mas com esforço.", Muscle.CARDIO, Muscle.QUADS).upTo(30).mins()
    val intervals = ex("Intervalos na bicicleta", 8, 30, 90, "30 segundos o mais rápido que conseguires, 90 segundos devagar. 8 vezes.", Muscle.CARDIO, Muscle.QUADS).secs()
}

private const val LIME = 0xFFE4F5B8
private const val SKY = 0xFFDCE3FC
private const val LILAC = 0xFFEADCF5
private const val SAND = 0xFFF6E6C3
private const val PEACH = 0xFFFADBD2
private const val MINT = 0xFFD3F0E4
private const val SAGE = 0xFFDDEBD0

private val progressTips = listOf(
    "Quando fizeres o topo das repetições em todas as séries, sobe o peso no treino seguinte (2,5 kg nos compostos, o mínimo possível nos halteres).",
    "Deixa 1 ou 2 repetições de reserva em cada série: cansado, mas com boa técnica.",
    "Regista os pesos na app. O que se mede, melhora.",
)

object GymPlans {
    val all: List<GymPlan> = listOf(
        // Programas semanais.
        GymPlan(
            id = "fullbody3", name = "Corpo inteiro 3x", short = "Full body", level = "Iniciante", perWeek = 3,
            goal = "Começar com a técnica certa",
            about = "Três treinos por semana que trabalham o corpo todo, alternando A e B. O melhor plano para quem está a começar ou volta depois de uma pausa.",
            weekdays = listOf(1, 3, 5), rotation = listOf(0, 1, 0),
            days = listOf(
                PlanDay("Treino A", "🅰️", 55, listOf(Moves.goblet, Moves.dbBench, Moves.pulldown, Moves.dbOhp, Moves.legCurl, Moves.plank), "Corpo inteiro"),
                PlanDay("Treino B", "🅱️", 55, listOf(Moves.legPress, Moves.dbRow, Moves.incline, Moves.glute, Moves.curl, Moves.pushdown, Moves.crunch), "Corpo inteiro"),
            ),
            colorArgb = SAGE,
            tips = listOf("Nas primeiras 2 semanas usa pesos leves e foca-te na técnica.") + progressTips,
        ),
        GymPlan(
            id = "upperlower4", name = "Superior / Inferior 4x", short = "Sup/Inf", level = "Intermédio", perWeek = 4,
            goal = "Ganhar força e massa",
            about = "Dois dias de tronco e dois de pernas, um de força e outro de volume. Cada músculo treina duas vezes por semana com descanso suficiente.",
            weekdays = listOf(1, 2, 4, 5), rotation = listOf(0, 1, 2, 3),
            days = listOf(
                PlanDay("Superior · força", "💪", 65, listOf(Moves.bench, Moves.row, Moves.ohp, Moves.pullup, Moves.skull, Moves.barCurl), "Peito · Costas · Ombros · Braços"),
                PlanDay("Inferior · força", "🦵", 65, listOf(Moves.squat, Moves.deadlift, Moves.bulgarian, Moves.legCurl, Moves.calves, Moves.plank), "Quadríceps · Posteriores · Glúteos"),
                PlanDay("Superior · volume", "💪", 60, listOf(Moves.incline, Moves.pulldown, Moves.seatedRow, Moves.lateral, Moves.cableFly, Moves.curl, Moves.pushdown), "Peito · Costas · Ombros · Braços"),
                PlanDay("Inferior · volume", "🦵", 60, listOf(Moves.legPress, Moves.hipThrust, Moves.legExt, Moves.legCurl, Moves.seatedCalves, Moves.legRaise), "Quadríceps · Glúteos · Abdómen"),
            ),
            colorArgb = SKY,
            tips = progressTips,
        ),
        GymPlan(
            id = "ppl", name = "Push / Pull / Pernas", short = "PPL", level = "Intermédio", perWeek = 3,
            goal = "Treino clássico de ginásio",
            about = "Empurrar (peito, ombros, tríceps), puxar (costas, bíceps) e pernas. Faz 3 dias, ou repete o ciclo para 6 dias se já tiveres experiência.",
            weekdays = listOf(1, 3, 5), rotation = listOf(0, 1, 2),
            days = listOf(
                PlanDay("Push", "🫸", 60, listOf(Moves.bench, Moves.dbOhp, Moves.incline, Moves.lateral, Moves.cableFly, Moves.pushdown, Moves.overhead), "Peito · Ombros · Tríceps"),
                PlanDay("Pull", "🫷", 60, listOf(Moves.pullup, Moves.row, Moves.seatedRow, Moves.facePull, Moves.hammer, Moves.curl), "Costas · Bíceps · Ombro posterior"),
                PlanDay("Pernas", "🦵", 65, listOf(Moves.squat, Moves.deadlift, Moves.legPress, Moves.legCurl, Moves.calves, Moves.legRaise), "Quadríceps · Posteriores · Glúteos · Gémeos"),
            ),
            colorArgb = LILAC,
            tips = progressTips,
        ),
        GymPlan(
            id = "split5", name = "Divisão 5 dias", short = "Split 5", level = "Avançado", perWeek = 5,
            goal = "Um grupo muscular por dia",
            about = "Cada dia é dedicado a um ou dois músculos, com muito volume. Para quem já treina há mais de 6 meses e consegue ir 5 vezes por semana.",
            weekdays = listOf(1, 2, 3, 4, 5), rotation = listOf(0, 1, 2, 3, 4),
            days = listOf(
                PlanDay("Peito e tríceps", "🫸", 65, listOf(Moves.bench, Moves.incline, Moves.chestDips, Moves.cableFly, Moves.skull, Moves.pushdown), "Peito · Tríceps"),
                PlanDay("Costas e bíceps", "🫷", 65, listOf(Moves.pullup, Moves.row, Moves.seatedRow, Moves.pullover, Moves.barCurl, Moves.hammer), "Costas · Bíceps"),
                PlanDay("Pernas", "🦵", 70, listOf(Moves.squat, Moves.legPress, Moves.deadlift, Moves.legExt, Moves.legCurl, Moves.calves), "Quadríceps · Posteriores · Gémeos"),
                PlanDay("Ombros e abdómen", "🎯", 55, listOf(Moves.ohp, Moves.lateral, Moves.rearDelt, Moves.shrug, Moves.legRaise, Moves.cableCrunch), "Ombros · Trapézio · Abdómen"),
                PlanDay("Braços", "💪", 50, listOf(Moves.closeBench, Moves.barCurl, Moves.overhead, Moves.inclineCurl, Moves.pushdown, Moves.hammer), "Bíceps · Tríceps"),
            ),
            colorArgb = LIME,
            tips = listOf("Se faltares a um dia, continua no seguinte: a app sugere sempre o próximo da lista.") + progressTips,
        ),
        GymPlan(
            id = "fatloss", name = "Perder gordura", short = "Definição", level = "Iniciante", perWeek = 4,
            goal = "Queimar calorias sem perder músculo",
            about = "Dois treinos de força para o corpo todo e dois de cardio com abdominais. Junta com o plano alimentar na aba Saúde.",
            weekdays = listOf(1, 2, 4, 6), rotation = listOf(0, 1, 2, 1),
            days = listOf(
                PlanDay("Força A", "🏋️", 50, listOf(Moves.goblet, Moves.dbBench, Moves.dbRow, Moves.hipThrust, Moves.plank), "Corpo inteiro"),
                PlanDay("Cardio + core", "🔥", 40, listOf(Moves.intervals, Moves.climbers, Moves.crunch, Moves.twist, Moves.plank), "Cardio · Abdómen", warmup = "5 min de bicicleta leve antes dos intervalos."),
                PlanDay("Força B", "🏋️", 50, listOf(Moves.lunge, Moves.pushup, Moves.pulldown, Moves.dbOhp, Moves.glute), "Corpo inteiro"),
            ),
            colorArgb = PEACH,
            tips = listOf("Mantém os pesos pesados: é isso que guarda o músculo enquanto perdes gordura.", "Tenta chegar aos passos do dia na aba Saúde."),
        ),

        // Treinos por músculo.
        GymPlan(
            id = "chest_tri", name = "Peito e tríceps", short = "Peito+Tri", level = "Todos", perWeek = 2,
            goal = "Peito cheio e braços firmes",
            about = "Um treino de empurrar completo: começa pesado no supino, passa à parte de cima do peito e acaba com o tríceps já quente.",
            weekdays = listOf(1, 4), rotation = listOf(0, 0),
            days = listOf(
                PlanDay("Peito e tríceps", "🫸", 65, listOf(Moves.bench, Moves.incline, Moves.chestDips, Moves.cableFly, Moves.pecDeck.sets(2), Moves.skull, Moves.pushdown, Moves.overhead.sets(2)), "Peito · Tríceps"),
            ),
            colorArgb = PEACH, kind = PlanKind.MUSCLE,
            tips = listOf("Junta com Costas e bíceps e Pernas para teres a semana feita.") + progressTips.take(2),
        ),
        GymPlan(
            id = "back_bi", name = "Costas e bíceps", short = "Costas+Bi", level = "Todos", perWeek = 2,
            goal = "Costas largas e bíceps",
            about = "Um treino de puxar: elevações e remadas para a largura e espessura das costas, e três exercícios de bíceps no fim.",
            weekdays = listOf(2, 5), rotation = listOf(0, 0),
            days = listOf(
                PlanDay("Costas e bíceps", "🫷", 65, listOf(Moves.pullup, Moves.row, Moves.pulldown, Moves.seatedRow, Moves.pullover, Moves.barCurl, Moves.inclineCurl, Moves.hammer.sets(2)), "Costas · Bíceps"),
            ),
            colorArgb = SKY, kind = PlanKind.MUSCLE,
            tips = listOf("Pensa em puxar com os cotovelos e não com as mãos: sentes mais as costas.") + progressTips.take(2),
        ),
        GymPlan(
            id = "legs", name = "Pernas completo", short = "Pernas", level = "Todos", perWeek = 1,
            goal = "Força e volume nas pernas",
            about = "Quadríceps, posteriores, glúteos e gémeos no mesmo dia. Duro, mas é o treino que mais faz pelo corpo todo.",
            weekdays = listOf(3), rotation = listOf(0),
            days = listOf(
                PlanDay("Pernas", "🦵", 75, listOf(Moves.squat, Moves.legPress, Moves.deadlift, Moves.bulgarian, Moves.legExt, Moves.legCurl, Moves.calves), "Quadríceps · Posteriores · Glúteos · Gémeos", warmup = "5 min de bicicleta, 10 agachamentos sem peso e 2 séries leves de agachamento."),
            ),
            colorArgb = MINT, kind = PlanKind.MUSCLE,
            tips = listOf("Descansa bem entre as séries pesadas de agachamento: 2 a 3 minutos.") + progressTips.take(2),
        ),
        GymPlan(
            id = "glutes", name = "Glúteos e posteriores", short = "Glúteos", level = "Todos", perWeek = 2,
            goal = "Glúteos fortes e definidos",
            about = "Hip thrust, peso morto romeno e afundos para a parte de trás das pernas, com abdutora no fim.",
            weekdays = listOf(2, 5), rotation = listOf(0, 0),
            days = listOf(
                PlanDay("Glúteos e posteriores", "🍑", 60, listOf(Moves.hipThrust, Moves.deadlift, Moves.bulgarian, Moves.legCurl, Moves.backExt, Moves.abductor), "Glúteos · Posteriores"),
            ),
            colorArgb = LILAC, kind = PlanKind.MUSCLE,
            tips = listOf("Aperta os glúteos 1 segundo no topo de cada repetição.") + progressTips.take(2),
        ),
        GymPlan(
            id = "shoulders", name = "Ombros e abdómen", short = "Ombros", level = "Todos", perWeek = 1,
            goal = "Ombros redondos e core forte",
            about = "As três partes do ombro (frente, lado e trás) e trapézio, a fechar com abdominais na polia e na barra.",
            weekdays = listOf(4), rotation = listOf(0),
            days = listOf(
                PlanDay("Ombros e abdómen", "🎯", 55, listOf(Moves.ohp, Moves.arnold, Moves.lateral, Moves.rearDelt, Moves.frontRaise.sets(2), Moves.shrug, Moves.legRaise, Moves.cableCrunch), "Ombros · Trapézio · Abdómen", warmup = "Rodar os ombros, 15 face pulls leves e 2 séries leves de press."),
            ),
            colorArgb = SAND, kind = PlanKind.MUSCLE,
            tips = listOf("Nas elevações laterais o peso é sempre leve: se balanças, é demais.") + progressTips.take(2),
        ),
        GymPlan(
            id = "arms", name = "Braços", short = "Braços", level = "Todos", perWeek = 1,
            goal = "Bíceps e tríceps em superséries",
            about = "Bíceps e tríceps alternados, sem descanso entre os dois exercícios de cada par. Rápido e com muito bombeamento.",
            weekdays = listOf(5), rotation = listOf(0),
            days = listOf(
                PlanDay("Braços", "💪", 45, listOf(Moves.closeBench, Moves.barCurl, Moves.skull, Moves.preacher, Moves.overhead, Moves.inclineCurl, Moves.pushdown, Moves.hammer), "Bíceps · Tríceps"),
            ),
            colorArgb = LIME, kind = PlanKind.MUSCLE,
            tips = listOf("Faz os exercícios aos pares (tríceps e depois bíceps) e descansa só no fim do par."),
        ),
        GymPlan(
            id = "core", name = "Abdómen em 20 minutos", short = "Abdómen", level = "Todos", perWeek = 3,
            goal = "Core forte para as costas e a postura",
            about = "Cinco exercícios em circuito. Dá para fazer no fim de outro treino ou sozinho no quarto.",
            weekdays = listOf(1, 3, 5), rotation = listOf(0, 0, 0),
            days = listOf(
                PlanDay("Abdómen", "🧱", 20, listOf(Moves.plank, Moves.deadBug, Moves.crunch, Moves.twist, Moves.climbers), "Abdómen", warmup = "1 min de jumping jacks e rodar a anca."),
            ),
            colorArgb = SAGE, kind = PlanKind.MUSCLE,
            tips = listOf("Na prancha, se a anca descer, para: é melhor menos tempo com boa postura."),
        ),

        // Em casa.
        GymPlan(
            id = "home3", name = "Em casa, sem material", short = "Casa", level = "Todos", perWeek = 3,
            goal = "Treinar no quarto ou na residência",
            about = "Só com o peso do corpo, em 30 minutos. Ideal para semanas de exames ou quando não dá para ir ao ginásio.",
            weekdays = listOf(1, 3, 6), rotation = listOf(0, 1, 0),
            days = listOf(
                PlanDay("Circuito força", "🏠", 30, listOf(Moves.bwSquat, Moves.pushup, Moves.towelRow, Moves.lunge, Moves.pikePush, Moves.dips, Moves.plank), "Corpo inteiro", warmup = "2 min de jumping jacks e rodar ombros e anca."),
                PlanDay("Circuito cardio", "🔥", 25, listOf(Moves.jacks, Moves.burpee, Moves.climbers, Moves.glute, Moves.superman, Moves.crunch), "Cardio · Abdómen", warmup = "2 min a marchar no lugar."),
            ),
            colorArgb = SAND, kind = PlanKind.HOME,
            tips = listOf("Quando ficar fácil, faz mais repetições ou desce mais devagar (3 segundos)."),
        ),
    )

    fun byId(id: String?): GymPlan? = all.firstOrNull { it.id == id }

    /** Title given to a workout started from a plan, so the next one in the rotation can be found. */
    fun title(plan: GymPlan, day: PlanDay): String = "${plan.short} · ${day.name}"

    /**
     * The day to train next: the one after the last workout done from this plan, or the first.
     * [doneTitles] are the titles of finished workouts, newest first.
     */
    fun next(plan: GymPlan, doneTitles: List<String>): PlanDay {
        val last = doneTitles.firstNotNullOfOrNull { t -> plan.rotation.indices.firstOrNull { title(plan, plan.days[plan.rotation[it]]) == t } }
        val slot = if (last == null) 0 else (last + 1) % plan.rotation.size
        return plan.days[plan.rotation[slot]]
    }
}
