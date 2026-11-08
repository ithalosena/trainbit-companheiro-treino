package com.ithalo.hybridcoach.core.game

import com.ithalo.hybridcoach.core.game.ItemKind.FRAME
import com.ithalo.hybridcoach.core.game.ItemKind.STICKER
import com.ithalo.hybridcoach.core.game.ItemKind.TITLE

/** Cosmetic collectibles, unlocked by level or by a specific trophy. */
object ItemCatalog {
    private fun level(n: Int) = ItemUnlock.Level(n)
    private fun trophy(id: String) = ItemUnlock.Achievement(id)

    val all: List<ItemDef> = listOf(
        // Títulos
        ItemDef("title_rookie", "Novato Promissor", "Título por chegar ao nível 2.", TITLE, "star", level(2)),
        ItemDef("title_gym_rat", "Rato de Academia", "Título do troféu Rato de Academia.", TITLE, "dumbbell", trophy("strength_10")),
        ItemDef("title_tireless", "O Incansável", "Título do troféu Combo x12.", TITLE, "flame", trophy("streak_12")),
        ItemDef("title_road_king", "Rei do Asfalto", "Título do troféu Centenário.", TITLE, "map", trophy("km_100")),
        ItemDef("title_iron_lord", "Senhor do Ferro", "Título do troféu Cem Toneladas.", TITLE, "coin", trophy("tonnage_100t")),
        ItemDef("title_marathoner", "Maratonista", "Título do troféu Maratonista.", TITLE, "mountain", trophy("run_42k")),
        ItemDef("title_record_hunter", "Caçador de Recordes", "Título do troféu Caçador de Recordes.", TITLE, "medal", trophy("prs_10")),
        ItemDef("title_stubborn", "Teimoso de Carteirinha", "Título do troféu oculto Persistência.", TITLE, "warning", trophy("three_fails")),
        ItemDef("title_legend", "Lenda do Pixel", "Título por chegar ao nível 99.", TITLE, "crown", level(99)),
        // Molduras de janela
        ItemDef("frame_classic", "Moldura Clássica", "A janela padrão, sempre disponível.", FRAME, "folder", level(1)),
        ItemDef("frame_dotted", "Moldura Pontilhada", "Moldura tracejada liberada no nível 5.", FRAME, "folder", level(5)),
        ItemDef("frame_striped", "Moldura Listrada", "Barra de título listrada liberada no nível 10.", FRAME, "folder", level(10)),
        ItemDef("frame_flames", "Moldura em Chamas", "Moldura do troféu Combo x4.", FRAME, "flame", trophy("streak_4")),
        ItemDef("frame_silver", "Moldura Prateada", "Moldura liberada no nível 20.", FRAME, "medal", level(20)),
        ItemDef("frame_gold", "Moldura Dourada", "Moldura do troféu Cem Sessões.", FRAME, "trophy", trophy("sessions_100")),
        ItemDef("frame_platinum", "Moldura de Platina", "Moldura do troféu Platina.", FRAME, "crown", trophy(AchievementCatalog.PLATINUM_ID)),
        // Adesivos
        ItemDef("sticker_heart", "Coração 8-bit", "Adesivo da primeira sessão.", STICKER, "heart", trophy("first_session")),
        ItemDef("sticker_dumbbell", "Halter de Bronze", "Adesivo da primeira série.", STICKER, "dumbbell", trophy("first_set")),
        ItemDef("sticker_shoe", "Tênis Alado", "Adesivo do troféu 5K na Conta.", STICKER, "shoe", trophy("run_5k")),
        ItemDef("sticker_floppy", "Disquete Raro", "Adesivo do troféu Salvar Jogo.", STICKER, "floppy", trophy("export_summary")),
        ItemDef("sticker_coin", "Moeda de Ouro", "Adesivo liberado no nível 15.", STICKER, "coin", level(15)),
        ItemDef("sticker_hourglass", "Ampulheta Eterna", "Adesivo do troféu Horas de Jogo.", STICKER, "hourglass", trophy("time_10h")),
        ItemDef("sticker_skull", "Caveira Sorridente", "Adesivo do troféu oculto No Limite.", STICKER, "skull", trophy("rpe10")),
        ItemDef("sticker_cartridge", "Cartucho Soprado", "Adesivo do troféu oculto Continue?.", STICKER, "lightning", trophy("comeback")),
    )
}
