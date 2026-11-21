package com.hpprint.app.data.papertemplate.model

/** 纸张分类（用于模板画廊分组） */
enum class PaperCategory(val label: String) {
    BASIC("基础线条"),
    CHINESE("中文练字"),
    MUSIC("音乐乐谱"),
    EDUCATION("学习教育"),
    CALLIGRAPHY("书法写作"),
    NOTE("笔记手账"),
    PLANNER("计划管理"),
    SPECIAL("专业图表"),
    OTHER("其他"),
}

/**
 * 全部纸张类型（与原站 74 种 + 自定义 一一对应）。
 * [defaultSpacing] / [defaultSecondary] 为默认主/次间距（毫米）。
 */
enum class PaperType(
    val key: String,
    val label: String,
    val category: PaperCategory,
    val defaultSpacing: Float = 8f,
    val defaultSecondary: Float = 8f,
) {
    // —— 基础线条 ——
    LINED("lined", "横线纸", PaperCategory.BASIC, 8f),
    VERTICAL_LINED("vertical_lined", "竖线纸", PaperCategory.BASIC, 10f),
    GRID("grid", "方格纸", PaperCategory.BASIC, 5f),
    DOT("dot", "点阵纸", PaperCategory.BASIC, 6f),
    BLANK("blank", "空白纸", PaperCategory.BASIC, 8f),
    GRID_LINED_SPLIT("grid_lined_split", "左半方格·右半横线", PaperCategory.BASIC, 5f, 8f),
    PINK_MILLIMETER_GRID("pink_millimeter_grid", "粉色毫米方格纸", PaperCategory.BASIC, 5f),

    // —— 中文练字 ——
    CHINESE_TIANZI("chinesetianzi", "田字格", PaperCategory.CHINESE, 15f),
    MIZIGE("mizige", "米字格", PaperCategory.CHINESE, 15f),
    JIUGONGGE("jiugongge", "九宫格", PaperCategory.CHINESE, 25f),
    HUIGONGGE("huigongge", "回宫格", PaperCategory.CHINESE, 15f),
    HARDPEN_HUIGONGGE("hardpen_huigongge", "硬笔回宫格", PaperCategory.CHINESE, 12f),
    CROSSGRID("crossgrid", "交叉格", PaperCategory.CHINESE, 10f),
    PINYIN_TIANZI("pinyin_tianzi", "拼音田字格", PaperCategory.CHINESE, 14f, 6f),
    COMPOSITION("composition_paper", "作文纸", PaperCategory.CHINESE, 9f),
    GENKOU_YOUSHI("genkou_youshi", "原稿用纸", PaperCategory.CHINESE, 8f),
    SEYES("seyes", "法式格线纸", PaperCategory.CHINESE, 8f),
    CN_EN_WORD("chinese_english_word_practice", "汉英单词练习纸", PaperCategory.CHINESE, 8f),
    CN_PREVIEW_CARD("chinese_preview_card", "语文预习卡", PaperCategory.CHINESE, 8f),
    ERROR_CORRECTION("error_correction", "错题本", PaperCategory.CHINESE, 9f),

    // —— 音乐乐谱 ——
    MUSIC("music", "音乐五线谱", PaperCategory.MUSIC, 3.2f),
    GUITAR_TAB("guitar_tab", "吉他六线谱", PaperCategory.MUSIC, 4f),
    BASS_TAB("bass_tab", "贝斯四线谱", PaperCategory.MUSIC, 4f),
    UKULELE("ukulele_staff_tab", "尤克里里谱", PaperCategory.MUSIC, 4f),
    GUITAR_CHORD_STAFF("guitar_chord_treble_staff", "吉他和弦五线谱", PaperCategory.MUSIC, 3.2f),

    // —— 学习教育 ——
    ENGLISH_PAPER("english_paper", "英语四线格", PaperCategory.EDUCATION, 5f),
    PINYIN_PAPER("pinyin_paper", "拼音四线格", PaperCategory.EDUCATION, 6f),
    ARITHMETIC("arithmetic_paper", "算术练习纸", PaperCategory.EDUCATION, 7f),
    PRACTICE("practice_paper", "数学练习纸", PaperCategory.EDUCATION, 8f),
    NUMBER_LINE("number_line", "数轴纸", PaperCategory.EDUCATION, 8f),
    FRACTION_BARS("fraction_bars", "分数条", PaperCategory.EDUCATION, 8f),
    TEN_FRAME("ten_frame", "十格表", PaperCategory.EDUCATION, 8f),
    CLOCK_FACES("clock_faces", "钟面练习纸", PaperCategory.EDUCATION, 8f),
    BRAILLE("braille_cell_sheet", "盲文点字纸", PaperCategory.EDUCATION, 4f),

    // —— 书法写作 ——
    CALLIGRAPHY("calligraphy", "书法练习纸", PaperCategory.CALLIGRAPHY, 12f),
    ENGLISH_CALLIGRAPHY("english_calligraphy", "英文书法纸", PaperCategory.CALLIGRAPHY, 7f),
    BRAZILIAN_CALLIGRAPHY("brazilian_calligraphy", "巴西书法练习纸", PaperCategory.CALLIGRAPHY, 7f),
    GERMAN_PRIMARY("german_primary", "德式三线练习纸", PaperCategory.CALLIGRAPHY, 6f),
    GERMAN_PRIMARY_BORDERED("german_primary_bordered", "德式三线带框", PaperCategory.CALLIGRAPHY, 6f),
    KINDER_HANDWRITING("kinder_handwriting", "美式幼儿书写纸", PaperCategory.CALLIGRAPHY, 7f),
    SLANT_GUIDE("slant_guide", "斜线引导纸", PaperCategory.CALLIGRAPHY, 8f),
    SLANT_RULED("slant_ruled", "斜线格纸", PaperCategory.CALLIGRAPHY, 8f),

    // —— 笔记手账 ——
    CORNELL("cornell", "康奈尔笔记纸", PaperCategory.NOTE, 7f),
    MEETING_NOTE("meeting_note", "会议笔记纸", PaperCategory.NOTE, 8f),
    STUDENT_NOTE("student_note", "学生笔记纸", PaperCategory.NOTE, 8f),
    MINIMAL_JOURNAL("minimal_journal", "简约手账纸", PaperCategory.NOTE, 8f),
    VINTAGE_NOTE("vintage_note", "复古笔记纸", PaperCategory.NOTE, 8f),
    CREATIVE_LOG("creative_log", "创意记录纸", PaperCategory.NOTE, 9f),
    SKETCH("sketch", "草图纸", PaperCategory.NOTE, 5f),

    // —— 计划管理 ——
    WEEKLY_PLANNER("weekly_planner", "周计划表", PaperCategory.PLANNER, 8f),
    DAILY_PLANNER("daily_planner", "日计划表", PaperCategory.PLANNER, 8f),
    MONTHLY_CALENDAR("monthly_calendar", "月历", PaperCategory.PLANNER, 8f),
    TASK_PLANNING("task_planning_sheet", "任务计划表", PaperCategory.PLANNER, 8f),
    PROJECT_PLANNING("project_planning", "项目规划纸", PaperCategory.PLANNER, 8f),
    MEAL_PLANNER("meal_planner", "膳食计划表", PaperCategory.PLANNER, 8f),
    HABIT_TRACKER("habit_tracker", "习惯打卡表", PaperCategory.PLANNER, 8f),
    WORKOUT_LOG("workout_log", "健身日志", PaperCategory.PLANNER, 8f),
    BUDGET_TRACKER("budget_tracker", "家庭预算表", PaperCategory.PLANNER, 8f),
    MEDICATION_TRACKER("medication_tracker", "用药追踪表", PaperCategory.PLANNER, 8f),
    GOAL_THERMOMETER("goal_thermometer", "目标温度计", PaperCategory.PLANNER, 8f),
    CHECKLIST("checklist_chart", "清单表", PaperCategory.PLANNER, 8f),
    RECIPE_CARD("recipe_card", "菜谱卡", PaperCategory.PLANNER, 8f),

    // —— 专业图表 ——
    ISOMETRIC("isometric", "等距网格纸", PaperCategory.SPECIAL, 8f),
    HEXAGONAL("hexagonal", "六边形网格纸", PaperCategory.SPECIAL, 8f),
    POLAR_GRID("polar_grid", "极坐标网格纸", PaperCategory.SPECIAL, 8f),
    LOG_LOG("log_log", "双对数纸", PaperCategory.SPECIAL, 8f),
    SEMI_LOG("semi_log", "半对数纸", PaperCategory.SPECIAL, 8f),
    KNITTING_GRAPH("knitting_graph", "编织图格纸", PaperCategory.SPECIAL, 8f),
    SEWING_GRID("sewing_grid", "缝纫裁剪格纸", PaperCategory.SPECIAL, 5f),
    PERSPECTIVE("perspective_sketch", "透视网格纸", PaperCategory.SPECIAL, 12f),
    STORYBOARD("storyboard", "分镜纸", PaperCategory.SPECIAL, 8f),
    SWOT("swot_analysis", "SWOT 分析表", PaperCategory.SPECIAL, 8f),
    CHILDREN_DRAWING("children_drawing", "儿童绘画纸", PaperCategory.SPECIAL, 10f),

    // —— 其他 ——
    BRANDED("branded", "品牌定制纸", PaperCategory.OTHER, 8f),
    CUSTOM("custom", "自定义纸", PaperCategory.OTHER, 8f),
    ;

    /** 默认配置 */
    fun defaultConfig(): PaperConfig = PaperConfig(spacingMm = defaultSpacing, secondaryMm = defaultSecondary)

    companion object {
        fun fromKey(key: String): PaperType = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}
