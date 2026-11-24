package io.github.pinkcarver8_blip.archfixture.s12.id;

import jakarta.persistence.EntityManager;

/** 위반: 공유 모듈이 DB 도구를 쓴다. */
class IdLookup {

    private final EntityManager entityManager;

    IdLookup(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    boolean open() {
        return entityManager.isOpen();
    }
}
