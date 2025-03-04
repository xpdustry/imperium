package com.xpdustry.imperium.common.string;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class StringTrieMapImplTest {

    @Test
    void test_put_get() {
        final var trie = new StringTrieMapImpl<Integer>();
        trie.put("test", 0);
        trie.put("test1", 1);
        trie.put("test2", 2);
        Assertions.assertEquals(0, trie.get("test"));
        Assertions.assertEquals(1, trie.get("test1"));
        Assertions.assertEquals(2, trie.get("test2"));
        Assertions.assertNull(trie.get("test3"));
    }

    @Test
    void test_contains() {
        final var trie = new StringTrieMapImpl<Integer>();
        trie.put("test", 0);
        Assertions.assertTrue(trie.contains("test", false));
        Assertions.assertTrue(trie.contains("test", true));
        Assertions.assertFalse(trie.contains("te", false));
        Assertions.assertTrue(trie.contains("te", true));
        Assertions.assertFalse(trie.contains("tex", false));
        Assertions.assertFalse(trie.contains("tex", true));
    }

    @Test
    void search_simple() {
        final var trie = new StringTrieMapImpl<Integer>();
        trie.put("dang", 0);
        trie.put("cat", 1);
        trie.put("catch", 2);
        final var result = trie.search("dang, this cat is hard to catch indeed");
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals(new StringTrieMap.Token<>("dang", 0, 0), result.get(0));
        Assertions.assertEquals(new StringTrieMap.Token<>("cat", 11, 1), result.get(1));
        Assertions.assertEquals(new StringTrieMap.Token<>("cat", 26, 1), result.get(2));
        Assertions.assertEquals(new StringTrieMap.Token<>("catch", 26, 2), result.get(3));
    }

    @Test
    void search_unicode() {
        final var trie = new StringTrieMapImpl<Integer>();
        trie.put("✏️", 0);
        trie.put("привет", 1);
        final var result = trie.search("test ✏️ привет");
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(new StringTrieMap.Token<>("✏️", 5, 0), result.get(0));
        Assertions.assertEquals(new StringTrieMap.Token<>("привет", 8, 1), result.get(1));
    }
}
