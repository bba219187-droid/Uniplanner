package com.uniplanner.app.domain

/** The muscles a drawing can light up; see the body figure on the gym screen. */
enum class Muscle { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, CORE, QUADS, HAMSTRINGS, GLUTES, CALVES, CARDIO }

data class PlanExercise(
    val name: String,
    val muscles: Set<Muscle>,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val how: String,
)

data class PlanDay(val name: String, val emoji: String, val minutes: Int, val exercises: List<PlanExercise>)

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
)

private fun ex(name: String, sets: Int, reps: Int, rest: Int, how: String, vararg m: Muscle) =
    PlanExercise(name, m.toSet(), sets, reps, rest, how)

private object Moves {
    val squat = ex("Agachamento", 3, 10, 90, "Pés à largura dos ombros, desce como se te sentasses numa cadeira, joelhos na direção dos pés e costas direitas.", Muscle.QUADS, Muscle.GLUTES)
    val goblet = ex("Agachamento goblet", 3, 12, 75, "Segura um haltere junto ao peito, desce com o peito alto e sobe a empurrar o chão.", Muscle.QUADS, Muscle.GLUTES, Muscle.CORE)
    val bench = ex("Supino reto", 3, 8, 120, "Deitado no banco, omoplatas juntas, desce a barra até ao peito e empurra até esticar os braços.", Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS)
    val dbBench = ex("Supino com halteres", 3, 10, 90, "Halteres ao lado do peito, empurra para cima e junta-os no topo sem bater.", Muscle.CHEST, Muscle.TRICEPS)
    val incline = ex("Supino inclinado com halteres", 3, 10, 90, "Banco a 30°, desce devagar até ao peito e empurra para cima.", Muscle.CHEST, Muscle.SHOULDERS)
    val row = ex("Remada com barra", 3, 10, 90, "Tronco inclinado e costas retas, puxa a barra até ao umbigo apertando as omoplatas.", Muscle.BACK, Muscle.BICEPS)
    val dbRow = ex("Remada unilateral", 3, 10, 75, "Mão e joelho no banco, puxa o haltere até à anca com o cotovelo junto ao corpo.", Muscle.BACK, Muscle.BICEPS)
    val pulldown = ex("Puxada na polia", 3, 10, 90, "Pega larga, puxa a barra até ao peito com o peito para fora e sobe devagar.", Muscle.BACK, Muscle.BICEPS)
    val pullup = ex("Elevações", 3, 6, 120, "Pendura-te com os braços esticados e puxa até o queixo passar a barra. Usa elástico se precisares.", Muscle.BACK, Muscle.BICEPS)
    val ohp = ex("Press militar", 3, 8, 90, "De pé, barra à frente dos ombros, empurra por cima da cabeça sem arquear as costas.", Muscle.SHOULDERS, Muscle.TRICEPS)
    val dbOhp = ex("Press de ombros com halteres", 3, 10, 75, "Sentado, halteres à altura das orelhas, empurra para cima até quase se tocarem.", Muscle.SHOULDERS, Muscle.TRICEPS)
    val lateral = ex("Elevações laterais", 3, 15, 60, "Braços ligeiramente fletidos, sobe os halteres até à altura dos ombros e desce devagar.", Muscle.SHOULDERS)
    val curl = ex("Curl de bíceps", 3, 12, 60, "Cotovelos colados ao corpo, sobe os halteres sem balançar o tronco.", Muscle.BICEPS)
    val hammer = ex("Curl martelo", 3, 12, 60, "Palmas viradas uma para a outra, sobe e desce com controlo.", Muscle.BICEPS)
    val pushdown = ex("Extensão de tríceps na polia", 3, 12, 60, "Cotovelos fixos junto ao corpo, empurra a corda para baixo até esticar.", Muscle.TRICEPS)
    val dips = ex("Mergulhos no banco", 3, 12, 60, "Mãos no banco atrás de ti, desce dobrando os cotovelos até 90° e sobe.", Muscle.TRICEPS, Muscle.CHEST)
    val deadlift = ex("Peso morto romeno", 3, 8, 120, "Joelhos ligeiramente fletidos, leva a anca atrás com a barra junto às pernas e sobe apertando os glúteos.", Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.BACK)
    val legPress = ex("Leg press", 3, 12, 90, "Pés a meio da plataforma, desce até 90° nos joelhos e empurra sem trancar.", Muscle.QUADS, Muscle.GLUTES)
    val lunge = ex("Afundos", 3, 10, 75, "Passo largo à frente, desce até o joelho de trás quase tocar no chão. Alterna as pernas.", Muscle.QUADS, Muscle.GLUTES)
    val legCurl = ex("Curl de pernas", 3, 12, 60, "Deitado na máquina, dobra os joelhos trazendo os calcanhares aos glúteos.", Muscle.HAMSTRINGS)
    val hipThrust = ex("Hip thrust", 3, 10, 90, "Costas apoiadas no banco, barra na anca, sobe até o corpo ficar reto e aperta os glúteos.", Muscle.GLUTES, Muscle.HAMSTRINGS)
    val calves = ex("Gémeos em pé", 3, 15, 45, "Sobe na ponta dos pés o mais alto possível e desce devagar.", Muscle.CALVES)
    val plank = ex("Prancha", 3, 40, 45, "Antebraços no chão e corpo em linha reta. As repetições são segundos.", Muscle.CORE)
    val crunch = ex("Abdominais crunch", 3, 15, 45, "Deitado, joelhos fletidos, sobe só os ombros do chão contraindo o abdómen.", Muscle.CORE)
    val pushup = ex("Flexões", 3, 12, 60, "Mãos à largura dos ombros, corpo reto, desce o peito até quase ao chão. Joelhos no chão se precisares.", Muscle.CHEST, Muscle.TRICEPS, Muscle.CORE)
    val bwSquat = ex("Agachamento livre", 3, 20, 60, "Sem peso, desce devagar e sobe com força.", Muscle.QUADS, Muscle.GLUTES)
    val glute = ex("Ponte de glúteos", 3, 15, 45, "Deitado de costas, pés no chão, sobe a anca e aperta os glúteos no topo.", Muscle.GLUTES, Muscle.HAMSTRINGS)
    val superman = ex("Superman", 3, 12, 45, "Deitado de barriga para baixo, levanta braços e pernas ao mesmo tempo e segura 2 segundos.", Muscle.BACK, Muscle.GLUTES)
    val burpee = ex("Burpees", 3, 10, 60, "Agacha, salta para prancha, faz uma flexão, volta e salta com os braços no ar.", Muscle.CARDIO, Muscle.CHEST, Muscle.QUADS)
    val climbers = ex("Mountain climbers", 3, 30, 45, "Em prancha, traz os joelhos ao peito alternadamente e depressa.", Muscle.CARDIO, Muscle.CORE)
    val jacks = ex("Jumping jacks", 3, 40, 30, "Salta abrindo pernas e braços e volta a fechar.", Muscle.CARDIO)
    val bike = ex("Bicicleta ou passadeira", 1, 20, 0, "Ritmo em que consegues falar mas com esforço. As repetições são minutos.", Muscle.CARDIO, Muscle.QUADS)
}

object GymPlans {
    val all: List<GymPlan> = listOf(
        GymPlan(
            id = "fullbody3", name = "Corpo inteiro 3x", short = "Full body", level = "Iniciante", perWeek = 3,
            goal = "Começar com a técnica certa",
            about = "Três treinos curtos por semana que trabalham o corpo todo. O melhor plano para quem está a começar ou volta depois de uma pausa.",
            weekdays = listOf(1, 3, 5), rotation = listOf(0, 1, 0),
            days = listOf(
                PlanDay("Treino A", "🅰️", 50, listOf(Moves.goblet, Moves.dbBench, Moves.pulldown, Moves.dbOhp, Moves.plank)),
                PlanDay("Treino B", "🅱️", 50, listOf(Moves.legPress, Moves.dbRow, Moves.incline, Moves.glute, Moves.curl, Moves.crunch)),
            ),
            colorArgb = 0xFFDDEBD0,
        ),
        GymPlan(
            id = "upperlower4", name = "Superior / Inferior 4x", short = "Sup/Inf", level = "Intermédio", perWeek = 4,
            goal = "Ganhar força e massa",
            about = "Dois dias de tronco e dois de pernas. Cada músculo treina duas vezes por semana com descanso suficiente.",
            weekdays = listOf(1, 2, 4, 5), rotation = listOf(0, 1, 2, 3),
            days = listOf(
                PlanDay("Superior · força", "💪", 60, listOf(Moves.bench, Moves.row, Moves.ohp, Moves.pullup, Moves.pushdown)),
                PlanDay("Inferior · força", "🦵", 60, listOf(Moves.squat, Moves.deadlift, Moves.lunge, Moves.calves, Moves.plank)),
                PlanDay("Superior · volume", "💪", 55, listOf(Moves.incline, Moves.pulldown, Moves.lateral, Moves.dbRow, Moves.curl, Moves.pushdown)),
                PlanDay("Inferior · volume", "🦵", 55, listOf(Moves.legPress, Moves.hipThrust, Moves.legCurl, Moves.calves, Moves.crunch)),
            ),
            colorArgb = 0xFFDCE3FC,
        ),
        GymPlan(
            id = "ppl", name = "Push / Pull / Pernas", short = "PPL", level = "Intermédio", perWeek = 3,
            goal = "Treino clássico de ginásio",
            about = "Empurrar (peito, ombros, tríceps), puxar (costas, bíceps) e pernas. Faz 3 dias, ou repete o ciclo para 6 dias se já tiveres experiência.",
            weekdays = listOf(1, 3, 5), rotation = listOf(0, 1, 2),
            days = listOf(
                PlanDay("Push", "🫸", 55, listOf(Moves.bench, Moves.dbOhp, Moves.incline, Moves.lateral, Moves.pushdown)),
                PlanDay("Pull", "🫷", 55, listOf(Moves.pullup, Moves.row, Moves.pulldown, Moves.hammer, Moves.curl)),
                PlanDay("Pernas", "🦵", 60, listOf(Moves.squat, Moves.deadlift, Moves.legPress, Moves.legCurl, Moves.calves)),
            ),
            colorArgb = 0xFFEADCF5,
        ),
        GymPlan(
            id = "home3", name = "Em casa, sem material", short = "Casa", level = "Todos", perWeek = 3,
            goal = "Treinar no quarto ou na residência",
            about = "Só com o peso do corpo, em 30 minutos. Ideal para semanas de exames ou quando não dá para ir ao ginásio.",
            weekdays = listOf(1, 3, 6), rotation = listOf(0, 1, 0),
            days = listOf(
                PlanDay("Circuito força", "🏠", 30, listOf(Moves.bwSquat, Moves.pushup, Moves.superman, Moves.lunge, Moves.dips, Moves.plank)),
                PlanDay("Circuito cardio", "🔥", 25, listOf(Moves.jacks, Moves.burpee, Moves.climbers, Moves.glute, Moves.crunch)),
            ),
            colorArgb = 0xFFF6E6C3,
        ),
        GymPlan(
            id = "fatloss", name = "Perder gordura", short = "Definição", level = "Iniciante", perWeek = 4,
            goal = "Queimar calorias sem perder músculo",
            about = "Dois treinos de força para o corpo todo e dois de cardio com abdominais. Junta com o plano alimentar na aba Saúde.",
            weekdays = listOf(1, 2, 4, 6), rotation = listOf(0, 1, 2, 1),
            days = listOf(
                PlanDay("Força A", "🏋️", 50, listOf(Moves.goblet, Moves.dbBench, Moves.dbRow, Moves.hipThrust, Moves.plank)),
                PlanDay("Cardio + core", "🔥", 40, listOf(Moves.bike, Moves.climbers, Moves.crunch, Moves.plank)),
                PlanDay("Força B", "🏋️", 50, listOf(Moves.lunge, Moves.pushup, Moves.pulldown, Moves.dbOhp, Moves.glute)),
            ),
            colorArgb = 0xFFFADBD2,
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
