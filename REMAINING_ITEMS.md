# Remaining Architecture / Quality Items

Bu dosya projede kalan iyileştirme maddelerini tek yerde tutar.
İlerledikçe maddeleri bu dosyadan silebiliriz.

## Durum Notu
- Bu listede sadece **kalan** maddeler var.
- Tamamlananlar bilinçli olarak yazılmadı.




## 3) Idempotent retry / merkezi retry policy eksik
- Neden önemli:
  Her noktada farklı retry davranışı tutarsızlık ve bakım yükü doğurur.
- Ne yapılmalı:
  Ortak bir retry policy helper (max deneme, exponential backoff, jitter, retryable/non-retryable ayrımı) tanımlanmalı.
- Kabul kriteri:
  Network/remote çağrılar aynı policy ile yönetiliyor olmalı.


## 9) Offline/cache stratejisi kısmi
- Neden önemli:
  Ağ yoksa deneyim düşer; her açılışta pahalı remote tarama gerekebilir.
- Ne yapılmalı:
  Local-first yaklaşımı güçlendir:
  Önce cache göster, sonra remote yenile.
  Cache geçerlilik (TTL) ve stale gösterimi ekle.
- Kabul kriteri:
  İnternetsiz durumda anlamlı ekran gösterilir; online olunca veri tazelenir.



## 11) Modülerleşme yok
- Neden önemli:
  Kod tabanı büyüdükçe derleme süresi, bağımlılık yönetimi ve sahiplik zorlaşır.
- Ne yapılmalı:
  Orta vadede `core`, `feature`, `data` gibi modül ayrımı planlanmalı.
- Kabul kriteri:
  En az bir feature ayrıştırılmış ve bağımlılık sınırları net olmalı.

## 12) Domain model immutability / side-effect kontrolü kısmi
- Neden önemli:
  Mutable yapıların yan etkisi bug üretir ve state yönetimini zorlaştırır.
- Ne yapılmalı:
  Domain modellerde mümkün olduğunca immutable yapı tercih edilmeli.
  Side-effect yapan mutasyonlar tek noktada toplanmalı.
- Kabul kriteri:
  Domain modellerde mutasyon minimal, akışlar deterministik olmalı.

## 13) Undo + paging edge-case yönetimi kısmi
- Neden önemli:
  Undo sırasında paging/sıralama/filtre etkileşimlerinde tutarsız görünüm olabilir.
- Ne yapılmalı:
  Undo davranışı paging + hidden state + selection için net kurallarla sabitlenmeli.
- Kabul kriteri:
  Undo her durumda tek ve beklenen sonucu üretmeli.

## 14) Localization tutarlılığı kısmi
- Neden önemli:
  TR/EN karışık metinler ve eksik çeviriler kullanıcı deneyimini düşürür.
- Ne yapılmalı:
  String kaynakları dil bazında düzenlenmeli (`values-tr`, `values-en` vb.).
  Hardcoded metinler tamamen kaldırılmalı.
- Kabul kriteri:
  Seçilen dillerde temel ekranlar tutarlı ve eksiksiz olmalı.

