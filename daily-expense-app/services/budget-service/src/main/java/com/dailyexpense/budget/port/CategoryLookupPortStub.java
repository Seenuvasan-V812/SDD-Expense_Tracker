package com.dailyexpense.budget.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Stub used in tests and when category-service base-url is blank. Always returns true. */
public class CategoryLookupPortStub implements CategoryLookupPort {

    private static final Logger log = LoggerFactory.getLogger(CategoryLookupPortStub.class);

    @Override
    public boolean exists(UUID categoryId, String bearerToken) {
        log.debug("CategoryLookupPortStub: assuming categoryId={} exists", categoryId);
        return true;
    }
}
