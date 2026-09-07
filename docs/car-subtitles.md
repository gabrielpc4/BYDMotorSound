# Car subtitles

This catalog supplies the subtitle beneath the selected car on the dashboard.
It currently covers every selectable profile discovered by the pack builder.

## Provenance and scope

- Performance facts are parsed from each included car's `ui_car.json` (or `dlc_ui_car.json`): 0-100 acceleration, torque (converted from Nm to kgfm), weight, and advertised BHP.
- The public [Assetto Corsa car-data structure reference](https://github.com/aiazzi-davide/AC_Car_Editor/blob/main/assettocorsa_car_data_documentation.md) documents this metadata boundary.
- The public [Assetto catalog](https://assetto.patacuack.net/cars?server=0) was used to cross-check naming. It is not used to overwrite the supplied mod variant's metadata.
- When a mod omits 0-100 in its UI metadata, a closest real-world estimate is used for that model family.
- Rounded road-car price bands are a Brazil-market indication informed by [Webmotors listings](https://www.webmotors.com.br/carros/estoque) (consulted 2026-09-04), not a quote or FIPE value.
- Race/prototype/community variants show no market price when Webmotors has no comparable listing.
- This avoids presenting a stock road-car engine claim for an Assetto race, stage, widebody or community-mod profile.

## Coverage

- `modded_car_packs`: 35 profiles.
- `original_cars_pack`: 106 profiles.

## Generated values

| Profile | Source name | Subtitle |
| --- | --- | --- |
| `alfa-romeo-4c` | Alfa Romeo 4C | 240 HP · 4,5s · 36 Kgfm · 925 Kg · ~ R$: 250.000–900.000 |
| `assetto-alfa-romeo-giulietta-qv` | Giulietta QV | 235 HP · 6,6s · 36 Kgfm · 1390 Kg · ~ R$: 100.000–500.000 |
| `assetto-alfa-romeo-giulietta-qv-le` | Giulietta QV Launch Edition 2014 | 240 HP · 6s · 36 Kgfm · 1390 Kg · ~ R$: 100.000–500.000 |
| `assetto-audi-r8-lms-2016` | Audi R8 LMS 2016 | 500 HP · 3,5s · 51 Kgfm · 1270 Kg · ~ R$: 500.000–900.000 |
| `assetto-audi-r8-plus` | Audi R8 V10 Plus | 550 HP · 3,5s · 55 Kgfm · 1620 Kg · ~ R$: 500.000–900.000 |
| `assetto-audi-tt-cup` | Audi TT Cup | 310 HP · 4,7s · 1125 Kg |
| `assetto-bmw-1m-s3` | BMW 1M Stage 3 | 400 HP · 3,8s · 59 Kgfm · 1495 Kg · ~ R$: 100.000–500.000 |
| `assetto-bmw-m3-e92-s1` | BMW M3 E92 Step1 | 414 HP · 4,3s · 41 Kgfm · 1605 Kg · ~ R$: 300.000–700.000 |
| `assetto-bmw-m3-gt2` | BMW M3 GT2 | 485 HP · 3,3s · 51 Kgfm · 1245 Kg · ~ R$: 300.000–700.000 |
| `assetto-bmw-m4` | BMW M4 | 431 HP · 3,9s · 56 Kgfm · 1572 Kg · ~ R$: 300.000–700.000 |
| `assetto-bmw-z4-gt3` | BMW Z4 GT3 | 530 HP · 3,3s · 53 Kgfm · 1265 Kg · ~ R$: 300.000–700.000 |
| `assetto-corvette-c7-stingray` | Chevrolet Corvette C7 Stingray | 455 HP · 3,8s · 64 Kgfm · 1496 Kg · ~ R$: 300.000–700.000 |
| `assetto-ferrari-458-gt2` | Ferrari 458 GT2 | 470 HP · 3,3s · 53 Kgfm · 1245 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-458-s3` | Ferrari 458 Italia Stage 3 | 570 HP · 3,8s · 55 Kgfm · 1140 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-488-gt3` | Ferrari 488 GT3 | 500 HP · 3,3s · 65 Kgfm · 1270 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-488-gtb` | Ferrari 488 GTB | 660 HP · 3s · 77 Kgfm · 1370 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-599xxevo` | Ferrari 599XX EVO | 750 HP · 2,5s · 71 Kgfm · 1310 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-f40` | Ferrari F40 | 478 HP · 3,5s · 59 Kgfm · 1175 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ferrari-fxx-k` | Ferrari FXX K | 1050 HP · 3s |
| `assetto-ferrari-laferrari` | Ferrari LaFerrari | 963 HP · 3s · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-alfa-giulia-qv` | Alfa Romeo Giulia Quadrifoglio | 510 HP · 3,9s · 61 Kgfm · 1595 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-alfa-mito-qv` | Alfa Romeo Mito QV | 168 HP · 7,3s · 25 Kgfm · 1140 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-audi-a1s1` | Audi S1 | 231 HP · 5,8s · 38 Kgfm · 1340 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-audi-r18-etron-quattro` | Audi R18 e-tron quattro 2014 | 700 HP · 3s · 915 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-audi-r8-lms` | Audi R8 LMS Ultra | 570 HP · 3,5s · 1250 Kg · ~ R$: 500.000–900.000 |
| `assetto-ks-audi-tt-vln` | Audi TT RS (VLN) | 390 HP · 3,5s · 54 Kgfm · 1120 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-bmw-m235i-racing` | BMW M235i Racing | 333 HP · 4,8s · 51 Kgfm · 1424 Kg |
| `assetto-ks-bmw-m4-akrapovic` | BMW M4 Akrapovic | 445 HP · 3,9s · 60 Kgfm · 1572 Kg · ~ R$: 300.000–700.000 |
| `assetto-ks-corvette-c7r` | Chevrolet Corvette C7R | 495 HP · 3,3s · 1320 Kg · ~ R$: 300.000–700.000 |
| `assetto-ks-ferrari-812-superfast` | Ferrari 812 Superfast | 800 HP · 2,9s · 73 Kgfm · 1525 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-ferrari-f138` | Ferrari F138 | 763 HP · 2,4s · 32 Kgfm · 642 Kg |
| `assetto-ks-ferrari-f2004` | Ferrari F2004 | 865 HP · 2,4s |
| `assetto-ks-ferrari-sf15t` | Ferrari SF15-T | 840 HP · 2,4s · 74 Kgfm · 702 Kg |
| `assetto-ks-ferrari-sf70h` | Ferrari SF70H | 2,4s |
| `assetto-ks-ford-mustang-2015` | Ford Mustang 2015 | 435 HP · 4,4s · 55 Kgfm · 1680 Kg · ~ R$: 250.000–600.000 |
| `assetto-ks-glickenhaus-scg003` | SCG 003C | 530 HP · 3,3s · 71 Kgfm · 1350 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-lamborghini-gallardo-sl-s3` | Lamborghini Gallardo SL Step3 | 1200 HP · 2,5s · 1390 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-lamborghini-huracan-gt3` | Lamborghini Huracan GT3 | 600 HP · 3,3s · 1239 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-lamborghini-sesto-elemento` | Lamborghini Sesto Elemento | 570 HP · 2,5s · 55 Kgfm · 999 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-maserati-alfieri` | Maserati Alfieri | 460 HP · 4,5s · 53 Kgfm · 1496 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-maserati-gt-mc-gt4` | Maserati GranTurismo MC GT4 | 430 HP · 3,3s · 55 Kgfm · 1410 Kg |
| `assetto-ks-maserati-levante` | Maserati Levante S | 424 HP · 5s · 59 Kgfm · 2108 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-maserati-mc12-gt1` | Maserati MC12 GT1 | 580 HP · 3,1s · 66 Kgfm · 1100 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-maserati-quattroporte` | Maserati Quattroporte GTS | 530 HP · 4,7s · 72 Kgfm · 1900 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-mazda-787b` | Mazda 787B | 690 HP · 2,9s · 62 Kgfm · 845 Kg · ~ R$: 100.000–300.000 |
| `assetto-ks-mazda-miata` | Mazda Miata NA | 130 HP · 8,8s · 15 Kgfm · 1040 Kg · ~ R$: 100.000–300.000 |
| `assetto-ks-mazda-rx7-spirit-r` | Mazda RX-7 Spirit R | 276 HP · 5,3s · 32 Kgfm · 1270 Kg · ~ R$: 100.000–300.000 |
| `assetto-ks-mazda-rx7-tuned` | Mazda RX-7 Tuned | 444 HP · 3,8s · 41 Kgfm · 1312 Kg · ~ R$: 100.000–300.000 |
| `assetto-ks-mclaren-570s` | McLaren 570S | 562 HP · 3,2s · 61 Kgfm · 1313 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-mclaren-650-gt3` | McLaren 650S GT3 | 500 HP · 3,3s · 51 Kgfm · 1240 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-mclaren-f1-gtr` | McLaren F1 GTR | 595 HP · 2,6s · 71 Kgfm · 1010 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-mclaren-p1` | McLaren P1™ | 903 HP · 2,8s · 92 Kgfm · 1450 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-mclaren-p1-gtr` | McLaren P1™ GTR | 986 HP · 2,4s · 1400 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-ks-mercedes-190-evo2` | Mercedes-Benz 190E EVO II | 370 HP · 4,7s · 32 Kgfm · 980 Kg |
| `assetto-ks-mercedes-c9` | Mercedes-Benz C9 1989 LM | 750 HP · 2,9s · 51 Kgfm · 905 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-nissan-gtr-gt3` | Nissan GT-R GT3 | 600 HP · 3,3s · 71 Kgfm · 1300 Kg · ~ R$: 500.000–900.000 |
| `assetto-ks-pagani-huayra-bc` | Pagani Huayra BC | 740 HP · 2,8s · 112 Kgfm · 1218 Kg · ~ R$: 8.000.000 |
| `assetto-ks-porsche-718-boxster-s` | Porsche 718 Boxster S | 350 HP · 4,6s · 43 Kgfm · 1355 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-718-boxster-s-pdk` | Porsche 718 Boxster S PDK | 350 HP · 4,2s · 43 Kgfm · 1385 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-718-cayman-s` | Porsche 718 Cayman S | 350 HP · 4,7s · 43 Kgfm · 1355 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-718-spyder-rs` | Porsche 718 RS 60 Spyder | 150 HP · 4,1s · 15 Kgfm · 580 Kg |
| `assetto-ks-porsche-911-gt3-cup-2017` | Porsche 911 GT3 Cup 2017 | 485 HP · 3,3s · 1220 Kg |
| `assetto-ks-porsche-911-gt3-r-2016` | Porsche 911 GT3 R 2016 | 500 HP · 3,3s · 1245 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-911-r` | Porsche 911 R | 500 HP · 3,8s · 47 Kgfm · 1370 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-911-rsr-2017` | Porsche 911 RSR 2017 | 510 HP · 3,5s · 1245 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-917-k` | Porsche 917 K | 600 HP · 6,8s · 820 Kg |
| `assetto-ks-porsche-918-spyder` | Porsche 918 Spyder | 887 HP · 2,6s · 130 Kgfm · 1640 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-919-hybrid-2015` | Porsche 919 Hybrid 2015 | 900 HP · 2,2s · 875 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-919-hybrid-2016` | Porsche 919 Hybrid 2016 | 900 HP · 2,2s · 875 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-991-carrera-s` | Porsche 911 Carrera S | 420 HP · 3,9s · 51 Kgfm · 1515 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-cayenne` | Porsche Cayenne Turbo S | 570 HP · 4,1s · 82 Kgfm · 2235 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-cayman-gt4-clubsport` | Porsche Cayman GT4 Clubsport | 385 HP · 3,3s · 43 Kgfm · 1300 Kg |
| `assetto-ks-porsche-cayman-gt4-std` | Porsche Cayman GT4 | 385 HP · 4,4s · 43 Kgfm · 1340 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-macan` | Porsche Macan Turbo | 400 HP · 4,8s · 56 Kgfm · 1925 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-porsche-panamera` | Porsche Panamera Turbo | 550 HP · 3,6s · 79 Kgfm · 2070 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-ks-praga-r1` | Praga R1 | 210 HP · 3,5s · 22 Kgfm · 585 Kg |
| `assetto-ks-ruf-rt12r-awd` | RUF RT12 R AWD | 730 HP · 3,4s · 96 Kgfm · 1 Kg · ~ R$: 250.000–900.000 |
| `assetto-ks-toyota-ae86` | Toyota AE86 | 122 HP · 9,2s · 14 Kgfm · 925 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-toyota-ae86-drift` | Toyota AE86 Drift | 185 HP · 4,2s · 21 Kgfm · 920 Kg · ~ R$: 100.000–500.000 |
| `assetto-ks-toyota-celica-st185` | Toyota Celica ST185 4WD Turbo | 295 HP · 5,5s · 47 Kgfm · 1045 Kg |
| `assetto-ks-toyota-gt86` | Toyota GT86 | 200 HP · 7,6s · 21 Kgfm · 1250 Kg · ~ R$: 250.000–600.000 |
| `assetto-ks-toyota-supra-mkiv-drift` | Toyota Supra MKIV Drift | 624 HP · 4,2s · 77 Kgfm · 1505 Kg · ~ R$: 250.000–600.000 |
| `assetto-ks-toyota-supra-mkiv-tuned` | Toyota Supra MKIV Time Attack | 690 HP · 4,2s · 77 Kgfm · 1505 Kg · ~ R$: 250.000–600.000 |
| `assetto-ks-toyota-ts040` | Toyota TS040 Hybrid 2014 | 1000 HP · 2,9s · ~ R$: 100.000–500.000 |
| `assetto-ktm-xbow-r` | KTM X-Bow R | 300 HP · 3,7s · 41 Kgfm · 795 Kg · ~ R$: 250.000–900.000 |
| `assetto-lamborghini-aventador-sv` | Lamborghini Aventador SV | 750 HP · 2,8s · 70 Kgfm · 1525 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-lamborghini-gallardo-sl` | Lamborghini Gallardo SL | 570 HP · 3,4s · 55 Kgfm · 1340 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-lamborghini-huracan-performante` | Lamborghini Huracan Performante | 640 HP · 2,9s · 61 Kgfm · 1382 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-lamborghini-huracan-st` | Lamborghini Huracan ST | 620 HP · 3,2s · 58 Kgfm · 1270 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-lotus-evora-gte-carbon` | Lotus Evora GTE Carbon | 420 HP · 4,4s · 47 Kgfm · 1285 Kg · ~ R$: 250.000–900.000 |
| `assetto-lotus-evora-gx` | Lotus Evora GX | 440 HP · 4,5s · 47 Kgfm · 1395 Kg |
| `assetto-mclaren-mp412c` | McLaren MP4-12C | 616 HP · 3,2s · 61 Kgfm · 1434 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-mclaren-mp412c-gt3` | McLaren MP4-12C GT3 | 500 HP · 3,3s · 51 Kgfm · 1245 Kg · ~ R$: 2.000.000–4.000.000 |
| `assetto-mercedes-amg-gt3` | Mercedes-Benz AMG GT3 | 520 HP · 3,3s · 61 Kgfm · 1265 Kg · ~ R$: 100.000–500.000 |
| `assetto-mercedes-sls` | Mercedes SLS AMG | 571 HP · 3,8s · 66 Kgfm · 1620 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-mercedes-sls-gt3` | Mercedes SLS AMG GT3 | 520 HP · 3,3s · 61 Kgfm · 1265 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-nissan-370z` | Nissan 370z Nismo | 350 HP · 5s · 38 Kgfm · 1498 Kg · ~ R$: 100.000–500.000 |
| `assetto-nissan-gtr` | Nissan GT-R NISMO | 592 HP · 2,1s · 66 Kgfm · 1750 Kg · ~ R$: 500.000–900.000 |
| `assetto-nissan-skyline-r34` | Nissan Skyline GTR R34 V-Spec | 325 HP · 4,9s · 40 Kgfm · 1560 Kg · ~ R$: 500.000–900.000 |
| `assetto-p4-5-2011` | P4/5 Competizione 2011 | 450 HP · 3,9s · 51 Kgfm · 1230 Kg · ~ R$: 100.000–500.000 |
| `assetto-pagani-huayra` | Pagani Huayra | 730 HP · 2,7s · 102 Kgfm · 1360 Kg · ~ R$: 8.000.000 |
| `assetto-pagani-zonda-r` | Pagani Zonda R | 750 HP · 2,7s · 72 Kgfm · 1070 Kg · ~ R$: 8.000.000 |
| `assetto-porsche-911-gt3-rs` | Porsche 911 GT3 RS | 500 HP · 3,1s · 47 Kgfm · 1420 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-porsche-991-turbo-s` | Porsche 911 Turbo S | 580 HP · 2,9s · 76 Kgfm · 1600 Kg · ~ R$: 700.000–2.000.000 |
| `assetto-tatuusfa1` | Tatuus FA01 | 198 HP · 2,6s · 23 Kgfm · 455 Kg |
| `assetto-toyota-supra-mkiv` | Toyota Supra MKIV | 280 HP · 5s · 47 Kgfm · 1510 Kg · ~ R$: 250.000–600.000 |
| `modded-aston-martin-dbrs9-gt3` | Aston Martin DBRS9 GT3 | 620 HP · 3,3s · 71 Kgfm · 1370 Kg |
| `modded-audi-r8-lms-gt2` | Audi R8 LMS GT2 | 569 HP · 3,3s · 58 Kgfm · 1350 Kg |
| `modded-audi-tt-cup-2015` | Audi TT Cup 2015 | 310 HP · 5s · 42 Kgfm · 1125 Kg |
| `modded-bmw-m8-gtlm` | BMW M8 GTLM | 500 HP · 3,3s · 1250 Kg |
| `modded-bugatti-chiron-pur-sport` | Bugatti Chiron Pur Sport | 1500 HP · 2,3s · 163 Kgfm · 1995 Kg |
| `modded-cadillac-escalade-esv` | Cadillac Escalade ESV ∣ 𝙂𝙞𝙤𝙧𝙜𝙞𝙆𝟬 | 403 HP · 6,8s · 58 Kgfm · 2235 Kg |
| `modded-chevrolet-camaro-concept` | Chevrolet Camaro Concept | 400 HP · 5,4s · 56 Kgfm · 1535 Kg |
| `modded-chevrolet-corvette-c6-z06-stanced` | Chevrolet Corvette [C6] Z06 Stanced | 505 HP · 4,2s · 65 Kgfm · 1420 Kg |
| `modded-chevrolet-corvette-c7-stingray-hellspec` | Chevrolet Corvette C7 Stingray hellspec 😈 | 3,8s · 103 Kgfm · 1496 Kg |
| `modded-ferrari-360-challenge-stradale` | Ferrari 360 Challenge Stradale manual | 4s · 38 Kgfm · 1280 Kg |
| `modded-ferrari-458-italia-gte-ferruccio` | Ferruccio | 465 HP · 3,4s · 46 Kgfm · 1245 Kg |
| `modded-ferrari-458-italia-tune` | Ferrari 458 Italia Tune | 574 HP · 3,8s · 55 Kgfm · 1140 Kg |
| `modded-ferrari-488-gte-evo-michelotto` | 2018 Ferrari 488 GTE Evo [Michelotto] | 583 HP · 3,3s · 82 Kgfm · 1270 Kg |
| `modded-ferrari-f1-2000` | Ferrari F1 2000 | 2,6s · 36 Kgfm · 535 Kg |
| `modded-ferrari-f430-gt2-2007` | GT2 3 - Ferrari F430 GT2 2007 | 550 HP · 3,3s · 50 Kgfm · 1100 Kg |
| `modded-ferrari-laferrari-trio` | Ferrari LaFerrari TRIO | 805 HP · 3s · 89 Kgfm · 1480 Kg |
| `modded-ferrari-sf90-xx-stradale-2024` | Ferrari SF90 XX Stradale 2024 | 1030 HP · 2,2s · 82 Kgfm · 1560 Kg |
| `modded-lamborghini-aventador-sv` | TGN | Lamborghini Aventador SV | 750 HP · 2,8s · 70 Kgfm · 1525 Kg |
| `modded-lamborghini-huracan-trofeo-evo2` | Lamborghini Huracan Trofeo EVO2 Hybrid | 700 HP · 3,1s · 71 Kgfm · 1376 Kg |
| `modded-lexus-lfa` | Lexus LFA | 552 HP · 3,7s · 49 Kgfm · 1480 Kg |
| `modded-lexus-lfa-concept-gt500` | Ghast | 2013 Lexus LFA Concept GT500 | 507 HP · 3,6s · 46 Kgfm · 1025 Kg |
| `modded-lexus-lfa-no-hesi-spec` | Lexus LFA | No Hesi Spec | 858 HP · 3,8s · 75 Kgfm · 1562 Kg |
| `modded-lexus-lfa-nurburgring-edition` | LFA Nurburgring Edition | 552 HP · 3,7s · 49 Kgfm · 1562 Kg |
| `modded-mercedes-amg-project-one-hypercar` | Mercedes-AMG Project One Hypercar | 760 HP · 2,5s · 1300 Kg |
| `modded-mercedes-benz-amg-gt3-evo-2020-sprint` | Mercedes-Benz AMG GT3 EVO 2020 Sprint | 520 HP · 3,3s · 61 Kgfm · 1265 Kg |
| `modded-mitsubishi-eclipse-gsx-r` | Mitsubishi Eclipse GSX-R | 5,9s · 60 Kgfm · 1100 Kg |
| `modded-mitsubishi-lancer-evolution-viii-gsr` | Mitsubishi Lancer Evolution VIII GSR | 305 HP · 5,9s · 39 Kgfm · 1400 Kg |
| `modded-nissan-350z` | Nissan 350Z | 301 HP · 5,8s · 42 Kgfm · 1510 Kg |
| `modded-nissan-370z-widebody` | Nissan 370Z Widebody | 1017 HP · 4,2s · 123 Kgfm · 1500 Kg |
| `modded-nissan-gt-r-nismo-godzilla` | Nissan GT-R NISMO Godzilla | 831 HP · 2,1s · 101 Kgfm · 1700 Kg |
| `modded-porsche-911-992-turbo-s-pdk` | Porsche 911 (992) Turbo S PDK | 968 HP · 2,4s · 121 Kgfm · 1640 Kg |
| `modded-porsche-911-gt3-rs-hellspec` | Porsche 911 GT3 RS hellspec | 3,1s · 66 Kgfm · 1395 Kg |
| `modded-porsche-911-turbo-s` | Sayrx Porsche 911 turboS 2022 | 640 HP · 2,7s · 77 Kgfm · 1850 Kg |
| `modded-porsche-carrera-gt-rs` | - a5 Porsche Carrera GT RS | 681 HP · 3,5s · 65 Kgfm · 1340 Kg |
| `modded-toyota-supra-wangan` | Toyota Supra Wangan | 648 HP · 3,8s · 71 Kgfm · 1432 Kg |

Regenerate after adding/removing a source car with:

```sh
python3 tools/generate_car_subtitles.py
```
