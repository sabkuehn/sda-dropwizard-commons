package org.sdase.commons.server.morphia.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Date;
import org.junit.Test;

public class ZonedDateTimeConverterTest {

   @Test
   public void shouldDecodeNull() {
      // given
      ZonedDateTimeConverter converter = new ZonedDateTimeConverter();

      // when
      Object result = converter.decode(ZonedDateTime.class, null, null);

      // then
      assertThat(result).isNull();
   }

   @Test
   public void shouldDecodeDate() {
      // given
      ZonedDateTimeConverter converter = new ZonedDateTimeConverter();
      Date d = new Date(119, 3, 21, 17, 22, 53); // NOSONAR

      // when
      ZonedDateTime result = (ZonedDateTime) converter.decode(ZonedDateTime.class, d, null);

      // then
      assertThat(result).isEqualTo("2019-04-21T17:22:53+01:00[Europe/Paris]");
   }

   @Test
   public void shouldDecodeString() {
      // given
      ZonedDateTimeConverter converter = new ZonedDateTimeConverter();

      // when
      ZonedDateTime result = (ZonedDateTime) converter
            .decode(ZonedDateTime.class, "2019-01-21T17:22:53+01:00[Europe/Paris]", null);

      // then
      assertThat(result).isEqualTo("2019-01-21T17:22:53+01:00[Europe/Paris]");
   }

   @Test
   public void shouldEncodeNull() {
      // given
      ZonedDateTimeConverter converter = new ZonedDateTimeConverter();

      // when
      Object result = converter.encode(null, null);

      // then
      assertThat(result).isNull();
   }

   @Test
   public void shouldEncodeZonedDateTime() {
      // given
      ZonedDateTimeConverter converter = new ZonedDateTimeConverter();

      // when
      Date result = (Date) converter.encode(ZonedDateTime.parse("2019-02-21T17:22:53+01:00[Europe/Paris]"), null);

      // then
      assertThat(result).isEqualTo("2019-02-21T17:22:53.000");
   }
}