/*
  ISH Dual Lexicon (Hmar Canonical + Top 25k English Loanwords)
  © Hmar Heritage Foundation
*/

const source: LexicalModelSource = {
  format: 'trie-1.0',
  wordBreaker: 'default',
  sources: ['wordlist.tsv'],
};

export default source;
