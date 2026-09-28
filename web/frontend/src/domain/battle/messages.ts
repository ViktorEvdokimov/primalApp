import type { BattleCommand, BattleState, RejectionReason, StanceChange } from './types';

/** Сообщения боя — тексты мобильного приложения (doc/battle.md §5). */
export const battleMessages = {
  damage(wounds: number, newStance: number | null): string {
    const parts: string[] = [];
    if (wounds > 0) parts.push(`Нанесено ран: ${wounds}.`);
    if (newStance !== null) parts.push(`Монстр перешёл на стойку ${newStance}!`);
    return parts.length > 0 ? parts.join(' ') : 'Урон накоплен, но рана не нанесена.';
  },
  damageWithoutThreshold: 'Урон накоплен, но рана не нанесена (нет порога раны).',
  damageReduced: (amount: number) => `Накопленный урон уменьшен на ${amount}.`,
  noAccumulatedDamage: 'Накопленного урона нет.',
  victory: (wounds: number) => `Монстр побеждён! Нанесено ран: ${wounds}`,
  healed: (health: number) => `Рана заживлена. Здоровье: ${health}`,
  stanceChanged: (stance: number) => `Монстр перешёл на стойку ${stance}!`,
  stanceConfirmed(stance: number, toughness: number | null, change: StanceChange): string {
    const threshold = toughness === null ? 'Порог раны отсутствует' : `Урон для раны: ${toughness}`;
    return `Стойка ${stance}. ${threshold}, смена: ${describeStanceChange(change)}`;
  },
  rage: (rage: number) => `Ярость: ${rage}`,
  rageSurge: (rage: number) =>
    `Выплеск ярости! Каждый охотник получил урон, равный силе монстра. Ярость сброшена до ${rage}`,
  hardened: (on: boolean) => (on ? 'Монстр затвердевший' : 'Монстр не затвердевший'),
  resilient: (on: boolean) => (on ? 'Стойка с устойчивостью' : 'Стойка без устойчивости'),
  roundStarted: (round: number, rage: number) => `Раунд ${round}. Ярость: ${rage}`,
  defeatByRounds: (maxRounds: number) => `Поражение! Прошло ${maxRounds} раундов.`,
  surrendered: 'Поражение! Вы сдались.',
  undone: (description: string) => `Отменено: ${description}`,
} as const;

/** Описание команды для истории отмены: «Отменить действие: урон +13» (doc/battle.md §6). */
export function describeCommand(state: BattleState, command: BattleCommand): string {
  switch (command.type) {
    case 'DEAL_DAMAGE':
      return command.amount >= 0 ? `урон +${command.amount}` : `снижение накопленного урона на ${-command.amount}`;
    case 'HEAL_WOUND':
      return 'заживление раны';
    case 'ADJUST_RAGE':
      return `ярость ${command.delta > 0 ? '+' : ''}${command.delta}`;
    case 'SET_STATUS': {
      const parts: string[] = [];
      if (command.hardened !== undefined) {
        parts.push(`${command.hardened ? 'включение' : 'снятие'} статуса «Затвердевший»`);
      }
      if (command.resilient !== undefined) {
        parts.push(`${command.resilient ? 'включение' : 'снятие'} «Устойчивости стойки»`);
      }
      return parts.join(', ');
    }
    case 'CHANGE_STANCE':
      return `смена на стойку ${state.monster.stance + 1}`;
    case 'CONFIRM_STANCE':
      return `параметры стойки ${state.pendingStance?.stance ?? state.monster.stance}`;
    case 'ACK_RAGE_SURGE':
      return 'выплеск ярости';
    case 'END_ROUND':
      return `завершение раунда ${state.round}`;
    case 'SURRENDER':
      return 'сдача';
  }
}

export function describeStanceChange(change: StanceChange): string {
  switch (change.mode) {
    case 'HEALTH':
      return `${change.atHealth} HP`;
    case 'ON_DEMAND':
      return 'по запросу';
    case 'FINAL':
      return 'нет (последняя стойка)';
  }
}

/** Почему команда не выполнена. Для INVALID_VALUE движок подставляет точное описание. */
export const rejectionMessages: Record<RejectionReason, string> = {
  BATTLE_OVER: 'Бой уже окончен.',
  STANCE_PENDING: 'Сначала подтвердите параметры новой стойки.',
  RAGE_SURGE_PENDING: 'Сначала подтвердите выплеск ярости.',
  NO_PENDING_STANCE: 'Смена стойки не ожидается.',
  NO_RAGE_SURGE: 'Выплеска ярости нет.',
  HEALTH_FULL: 'Здоровье уже максимальное',
  STANCE_NOT_ON_DEMAND: 'Эта стойка меняется не по запросу.',
  LAST_STANCE: 'Это последняя стойка.',
  INVALID_VALUE: 'Недопустимое значение.',
};

export const invalidValueMessages = {
  hunterCount: 'Число охотников — целое число больше 0.',
  toughness: 'Прочность — целое число больше 0 или пусто, если порога раны нет.',
  atHealth: 'Порог смены стойки — здоровье от 1 до 9.',
  health: 'Здоровье — целое число от 1 до 10.',
  damage: 'Урон — целое число, отличное от 0.',
  rage: 'Изменение ярости — целое число, отличное от 0.',
  status: 'Не указан статус монстра.',
} as const;
