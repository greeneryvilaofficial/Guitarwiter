/**
 * Multi-language support for keyboard UI
 */

export type Language = 'id' | 'en';

interface Translations {
  [key: string]: {
    [key in Language]: string;
  };
}

const translations: Translations = {
  'keyboard.title': {
    id: 'Keyboard Gitar',
    en: 'Guitar Keyboard',
  },
  'keyboard.listening': {
    id: 'Mendengarkan… petik nada',
    en: 'Listening… pick a note',
  },
  'keyboard.letter-mode': {
    id: 'Mode huruf — petik nada untuk mengetik',
    en: 'Letter mode — pick notes to type',
  },
  'keyboard.symbol-mode': {
    id: 'Mode simbol — petik nada untuk tanda baca',
    en: 'Symbol mode — pick notes for punctuation',
  },
  'keyboard.emoji-mode': {
    id: 'Mode emoji — petik nada untuk emoji',
    en: 'Emoji mode — pick notes for emoji',
  },
  'keyboard.backspace': {
    id: 'Hapus',
    en: 'Delete',
  },
  'keyboard.enter': {
    id: 'Masuk',
    en: 'Enter',
  },
  'keyboard.shift': {
    id: 'Geser',
    en: 'Shift',
  },
  'keyboard.space': {
    id: 'Spasi',
    en: 'Space',
  },
  'theme.dark': {
    id: 'Gelap',
    en: 'Dark',
  },
  'theme.light': {
    id: 'Terang',
    en: 'Light',
  },
  'theme.blue': {
    id: 'Biru',
    en: 'Blue',
  },
  'theme.purple': {
    id: 'Ungu',
    en: 'Purple',
  },
  'theme.green': {
    id: 'Hijau',
    en: 'Green',
  },
};

export const t = (key: string, lang: Language = 'id'): string => {
  return translations[key]?.[lang] || key;
};

export const getAvailableLanguages = (): Language[] => ['id', 'en'];

export default {
  t,
  getAvailableLanguages,
  translations,
};