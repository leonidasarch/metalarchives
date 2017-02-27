package com.github.loki.afro.metallum.search.service;

import com.github.loki.afro.metallum.core.parser.search.LyricalThemesSearchParser;

public class LyricalThemesSearchService extends AbstractBandSearchService {

    public LyricalThemesSearchService() {
        super(0, false, false, false, false);
    }

    @Override
    protected final LyricalThemesSearchParser getSearchParser() {
        return new LyricalThemesSearchParser();
    }

}
