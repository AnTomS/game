package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));
        commands.put("about", (ctx, a) -> {
            System.out.println("=== DungeonMini ===");
            System.out.println("Консольная игра-приключение");
            System.out.println("Исследуйте комнаты, собирайте предметы, сражайтесь с монстрами!");
            System.out.println("Используйте 'help' для списка команд");
        });
        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory();
            long total = rt.totalMemory();
            long used = total - free;
            long max = rt.maxMemory();
            
            System.out.println("=== Статистика сборки мусора ===");
            System.out.println("Используется: " + formatBytes(used) + " (" + used + " байт)");
            System.out.println("Свободно: " + formatBytes(free) + " (" + free + " байт)");
            System.out.println("Всего выделено: " + formatBytes(total) + " (" + total + " байт)");
            System.out.println("Максимум: " + formatBytes(max) + " (" + max + " байт)");
            System.out.println("Процент использования: " + String.format("%.1f", (double) used / max * 100) + "%");
        });
        commands.put("alloc", (ctx, a) -> {
            System.out.println("Выделяем память для демонстрации GC...");
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                tempList.add("Временная строка " + i + " для демонстрации сборки мусора");
            }
            System.out.println("Создано " + tempList.size() + " объектов");
            System.out.println("Память после выделения:");
            commands.get("gc-stats").execute(ctx, a);

            System.out.println("Объекты готовы к сборке мусора");
        });
        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));
        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление: north, south, east, west");
            }
            String direction = a.get(0).toLowerCase();
            Room current = ctx.getCurrent();
            Room next = current.getNeighbors().get(direction);
            if (next == null) {
                throw new InvalidCommandException("Нет пути в направлении: " + direction);
            }
            
            // Проверка двери в склепе
            if (current.getName().equals("Склеп") && direction.equals("south")) {
                Player player = ctx.getPlayer();
                boolean hasKey = player.getInventory().stream()
                    .anyMatch(item -> item instanceof Key);
                
                if (!hasKey) {
                    throw new InvalidCommandException("Дверь заперта! Нужен ключ для входа в сокровищницу.");
                }
                System.out.println("Вы используете ключ и открываете дверь в сокровищницу!");
            }
            
            ctx.setCurrent(next);
            System.out.println("Вы перешли в: " + next.getName());
        });
        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }
            String itemName = String.join(" ", a);
            Room current = ctx.getCurrent();
            Player player = ctx.getPlayer();
            
            // Ищем предмет в комнате по имени
            Item foundItem = current.getItems().stream()
                .filter(item -> item.getName().equals(itemName))
                .findFirst()
                .orElse(null);
            
            if (foundItem == null) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в комнате");
            }
            
            // Перемещаем предмет из комнаты в инвентарь
            current.getItems().remove(foundItem);
            player.getInventory().add(foundItem);
            System.out.println("Взято: " + foundItem.getName());
        });
        commands.put("inventory", (ctx, a) -> {
            Player player = ctx.getPlayer();
            List<Item> inventory = player.getInventory();
            
            if (inventory.isEmpty()) {
                System.out.println("Инвентарь пуст");
                return;
            }
            
            // Группируем предметы по типу и сортируем
            Map<String, List<Item>> groupedItems = inventory.stream()
                .collect(Collectors.groupingBy(item -> item.getClass().getSimpleName()));
            
            // Выводим сгруппированные предметы
            groupedItems.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String itemType = entry.getKey();
                    List<Item> items = entry.getValue();
                    System.out.println("- " + itemType + " (" + items.size() + "): " + 
                        items.stream()
                            .map(Item::getName)
                            .sorted()
                            .collect(Collectors.joining(", ")));
                });
        });
        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }
            String itemName = String.join(" ", a);
            Player player = ctx.getPlayer();
            
            // Ищем предмет в инвентаре по имени
            Item foundItem = player.getInventory().stream()
                .filter(item -> item.getName().equals(itemName))
                .findFirst()
                .orElse(null);
            
            if (foundItem == null) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в инвентаре");
            }
            
            // Применяем предмет (полиморфизм через Item.apply())
            foundItem.apply(ctx);
        });
        commands.put("fight", (ctx, a) -> {
            Room current = ctx.getCurrent();
            Monster monster = current.getMonster();
            Player player = ctx.getPlayer();
            
            if (monster == null) {
                throw new InvalidCommandException("В этой комнате нет монстра для боя");
            }
            
            if (monster.getHp() <= 0) {
                throw new InvalidCommandException("Монстр уже побежден");
            }
            
            if (player.getHp() <= 0) {
                throw new InvalidCommandException("Вы мертвы и не можете сражаться");
            }
            
            // Атака игрока
            int playerDamage = player.getAttack();
            monster.setHp(monster.getHp() - playerDamage);
            System.out.println("Вы бьёте " + monster.getName() + " на " + playerDamage + 
                ". HP монстра: " + Math.max(0, monster.getHp()));
            
            // Проверяем, побежден ли монстр
            if (monster.getHp() <= 0) {
                System.out.println("Монстр " + monster.getName() + " побежден!");
                // Монстр может выпасть лут (здесь просто убираем его)
                current.setMonster(null);
                ctx.addScore(10); // Бонус за победу
                return;
            }
            
            // Атака монстра
            int monsterDamage = monster.getLevel(); // Простая формула урона
            player.setHp(player.getHp() - monsterDamage);
            System.out.println("Монстр отвечает на " + monsterDamage + ". Ваше HP: " + player.getHp());
            
            // Проверяем, умер ли игрок
            if (player.getHp() <= 0) {
                System.out.println("Вы погибли! Игра окончена.");
                System.out.println("Финальный счет: " + ctx.getScore());
                System.exit(0);
            }
        });
        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        // Основные локации
        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        
        // Новые локации
        Room cemetery = new Room("Кладбище", "Мрачное кладбище с надгробиями и вороньим карканьем.");
        Room crypt = new Room("Склеп", "Древний склеп с массивной дверью. В воздухе витает запах тлена.");
        Room treasure = new Room("Сокровищница", "Блестящая сокровищница, полная золота и драгоценностей!");
        
        // Связи между локациями
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        forest.getNeighbors().put("north", cemetery);  // Новый путь в кладбище
        cave.getNeighbors().put("west", forest);
        
        cemetery.getNeighbors().put("south", forest);
        cemetery.getNeighbors().put("east", crypt);   // Путь в склеп
        crypt.getNeighbors().put("west", cemetery);
        crypt.getNeighbors().put("south", treasure);  // Путь в сокровищницу (через дверь)
        treasure.getNeighbors().put("north", crypt);

        // Предметы и монстры в существующих локациях
        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8));
        
        // Новые предметы и монстры
        cemetery.getItems().add(new Key("Старый ключ"));
        cemetery.setMonster(new Monster("Зомби", 2, 6));
        
        // Сокровищница содержит золото
        treasure.getItems().add(new Gold("Золотой слиток", 50));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                
                /* 
                 * ПРИМЕРЫ ОБРАБОТКИ ИСКЛЮЧЕНИЙ:
                 * 
                 * 1. Ошибки компиляции (обнаруживаются на этапе компиляции):
                 *    - String s = 123; // Ошибка: несовместимые типы
                 *    - System.out.println(undefinedVariable); // Ошибка: переменная не найдена
                 *    - int x = "строка"; // Ошибка: несовместимые типы
                 * 
                 * 2. Ошибки выполнения (RuntimeException и другие исключения):
                 *    - int result = 10 / 0; // ArithmeticException: деление на ноль
                 *    - String s = null; s.length(); // NullPointerException
                 *    - int[] arr = new int[5]; arr[10] = 1; // ArrayIndexOutOfBoundsException
                 * 
                 * В нашем коде мы обрабатываем:
                 * - InvalidCommandException: пользовательские ошибки (неправильные команды)
                 * - Exception: все остальные непредвиденные ошибки
                 */
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
