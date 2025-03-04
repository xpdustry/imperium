package com.xpdustry.imperium.common.collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class CharTrieMapImplTest {

    @Test
    void test_put_get() {
        final var trie = new CharTrieMapImpl<Integer>();
        trie.put("test".toCharArray(), 0);
        trie.put("test1".toCharArray(), 1);
        trie.put("test2".toCharArray(), 2);
        Assertions.assertEquals(0, trie.get("test".toCharArray()));
        Assertions.assertEquals(1, trie.get("test1".toCharArray()));
        Assertions.assertEquals(2, trie.get("test2".toCharArray()));
        Assertions.assertNull(trie.get("test3".toCharArray()));
    }

    @Test
    void test_contains() {
        final var trie = new CharTrieMapImpl<Integer>();
        trie.put("test".toCharArray(), 0);
        Assertions.assertTrue(trie.contains("test".toCharArray(), false));
        Assertions.assertTrue(trie.contains("test".toCharArray(), true));
        Assertions.assertFalse(trie.contains("te".toCharArray(), false));
        Assertions.assertTrue(trie.contains("te".toCharArray(), true));
        Assertions.assertFalse(trie.contains("tex".toCharArray(), false));
        Assertions.assertFalse(trie.contains("tex".toCharArray(), true));
    }

    @Test
    void search_simple() {
        final var trie = new CharTrieMapImpl<Integer>();
        trie.put("dang".toCharArray(), 0);
        trie.put("cat".toCharArray(), 1);
        trie.put("catch".toCharArray(), 2);
        final var result = trie.search("dang, this cat is hard to catch indeed");
        Assertions.assertEquals(4, result.size());
        Assertions.assertEquals(new CharTrieMap.Token<>("dang", 0, 0), result.get(0));
        Assertions.assertEquals(new CharTrieMap.Token<>("cat", 11, 1), result.get(1));
        Assertions.assertEquals(new CharTrieMap.Token<>("cat", 26, 1), result.get(2));
        Assertions.assertEquals(new CharTrieMap.Token<>("catch", 26, 2), result.get(3));
    }

    @Test
    void search_unicode() {
        final var trie = new CharTrieMapImpl<Integer>();
        trie.put("✏️".toCharArray(), 0);
        trie.put("привет".toCharArray(), 1);
        final var result = trie.search("test ✏️ привет");
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(new CharTrieMap.Token<>("✏️", 5, 0), result.get(0));
        Assertions.assertEquals(new CharTrieMap.Token<>("привет", 8, 1), result.get(1));
    }
}
