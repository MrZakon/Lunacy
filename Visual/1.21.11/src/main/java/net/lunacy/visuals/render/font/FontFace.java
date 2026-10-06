package net.lunacy.visuals.render.font;

/** Original bundled faces used by the supplied menu implementation. */
public enum FontFace {
    SF_REGULAR("sf_regular"),
    SF_MEDIUM("sf_medium"),
    SF_SEMIBOLD("sf_semibold"),
    GOLOS("golos"),
    GOLOS_SEMI("golos_semi"),
    UNBOUNDED("unbounded"),
    UNBOUNDED_LIGHT("unbounded_light"),
    PLEX_MONO("plexmono"),
    ROUND("round"),
    ICONS_2("icons2");

    private final String file;

    FontFace(String file) {
        this.file = file;
    }

    public String file() {
        return file;
    }
}
