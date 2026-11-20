# AI Companion NPC — development fork

This repository is a working copy of nekostul/nekostulAICompanion for independent development of the Minecraft AI Companion project.

Upstream source: https://github.com/nekostul/nekostulAICompanion
Upstream license: MIT. The original LICENSE and attribution notices are preserved.

## Project direction

Target: Minecraft Java Edition 1.20.1, Forge.
Planned work: local LLM integration (Ollama), persistent memory, autonomous goals, and optional voice input/output.

## Upstream project README

# AI Companion NPC

Мод для Minecraft Java Edition с NPC-напарником `nekostulAI`.

NPC управляется через чат и команды, помогает с добычей ресурсов, перемещением, домом и инвентарём.
Мод работает и без LLM: ИИ-интеграция опциональна.

---

## Ключевые возможности

- Режимы поведения: следовать за игроком и стоп.
- Добыча ресурсов по команде (включая последовательности задач: `сначала ... потом ...`, `... и ...`).
- Работа с вёдрами (вода/лава), добыча и крафт факелов.
- Система дома (`/ainpc sethome`, возврат домой, подтверждения в дальних сценариях).
- Команда `где ты` с координатами и кнопкой телепорта.
- GUI панели экипировки NPC через `/ainpc gui`.
- Поддержка party-доступа (владелец может добавить/удалить игроков).

## Инвентарь и выживание

- Основной инвентарь NPC: 36 слотов.
- Отдельные слоты для инструментов, еды и брони.
- Подбор и выдача предметов через команды и взаимодействие.
- Система голода и здоровья.
- Охота на мирных мобов при нехватке еды.

## Опциональная LLM-интеграция

В исходном проекте предусмотрена опциональная интеграция YandexGPT. Наша цель — добавить локальный LLM-провайдер, не ломая работу NPC без модели.

## Лицензия и авторство

Исходный проект создан nekostul и распространяется по лицензии MIT. См. файл LICENSE и CREDITS.txt.
