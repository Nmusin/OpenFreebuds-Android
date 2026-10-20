# OpenFreebuds-Android

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)

Лёгкий нативный Open-Source клиент для наушников **HUAWEI FreeBuds** (с полной аппаратной поддержкой **FreeBuds 5i** и других моделей Huawei SPP).

Разработано на основе обратного инжиниринга официального плагина Huawei AI Life и анализа SPP-пакетов реального устройства.

---

## Возможности

- 🔋 **Отображение заряда**: точный уровень заряда левого/правого наушника и зарядного кейса (с корректной обработкой наушников в кейсе без ложных 0%).
- 🎧 **Управление шумоподавлением**:
  - Выключено (Off)
  - Активное шумоподавление (ANC On: Cozy, General, Ultra)
  - Режим звукопроницаемости / прозрачности (Awareness)
- 🌬️ **Подавление шума ветра**: аппаратный переключатель с учетом инверсной полярности шины `2B94`.
- 🎚️ **Эквалайзер**: встроенные пресеты (Басы, Высокие, Голос) и поддержка 10-полосных кастомных профилей (слоты 100–102).
- 🔔 **Поиск наушников (Find My Buds)**: воспроизведение громкого писка на левом или правом наушнике по отдельности.
- ⚡ **Стабильное соединение**: автоматический handshake телеметрии `0A0E` сразу при подключении для предотвращения спонтанных дисконнектов прошивки.
- 🔄 **Фоновая служба (Foreground Service)**: мониторинг статуса в фоне через системную службу Android без жора батареи.

---

## Архитектура проекта

Приложение написано на **Kotlin** с декларативным интерфейсом на **Jetpack Compose**:

```text
app/src/main/java/ru/nmusin/openfreebuds/
├── protocol/               # Реализация протокола Huawei SPP
│   ├── HuaweiCrc.kt        # CRC16/XMODEM расчет и верификация
│   ├── HuaweiFrame.kt      # Парсер и сборщик TLV-фреймов (5A ... CRC)
│   └── FreeBuds5iProtocol.kt# Команды (0A0E, 0108, 2B04, 2B49, 2B94, 2B5D)
├── bluetooth/              # Управление каналом связи
│   └── BluetoothRfcommManager.kt # RFCOMM сокет (канал 16 / SPP UUID)
├── service/                # Фоновый мониторинг
│   └── FreeBudsService.kt  # Android Foreground Service
├── model/                  # Модели данных (BatteryState, AncMode, etc.)
└── ui/                     # Jetpack Compose экраны и темы Material 3
    ├── MainActivity.kt     # Подключение, разрешения Bluetooth
    ├── MainScreen.kt       # Основной дашборд управления
    └── theme/              # Цветовая палитра и стили
```

---

## Протокол Huawei SPP

Пакеты передаются через Bluetooth Classic RFCOMM (канал 16):

```text
5A <len:2 BE> 00 <service:1> <command:1> <TLV...> <CRC16/XMODEM:2 BE>
```

| Функция | Опкод | Назначение |
|---|---|---|
| BigData Handshake | `0A 0E` | Ответ `02 01 00` для предотвращения обрывов связи |
| Батарея | `01 08` / `01 27` | Чтение и push-уведомление заряда (0x00 = в кейсе) |
| Шумоподавление | `2B 04` / `2B 2A` | Запись и чтение режимов (Off, ANC, Awareness) |
| Подавление ветра | `2B 94` / `2B 95` | Управление шумоподавлением ветра (инверсно: 0=On, 1=Off) |
| Эквалайзер | `2B 49` / `2B 4A` | 10-полосный эквалайзер (порядок тегов `1, 2, 5, 3, 4`) |
| Поиск наушников | `2B 5D` / `2B 5E` | Активация звукового сигнала поиска |
| Идентификация | `2B 0A` | Версия прошивки, серийный номер, имя модели |

---

## Сборка и запуск

Требования:
- Android Studio Ladybug / Meerkat или выше
- JDK 21
- Android SDK 35 (минимальная поддерживаемая версия: Android 8.0 / API 26)

```bash
git clone https://github.com/Nmusin/OpenFreebuds-Android.git
cd OpenFreebuds-Android

# Сборка debug APK
./gradlew assembleDebug

# Запуск тестов протокола
./gradlew test
```

---

## Лицензия

GNU General Public License v3.0 (GPL-3.0)
