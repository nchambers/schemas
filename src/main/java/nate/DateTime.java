package nate;


public class DateTime {
  //  GregorianCalendar start;
  //  GregorianCalendar end;
  int year = -1;
  int month = -1;
  int day = -1;

  DateTime(int y, int m, int d) {
    year = y;
    month = m;
    day = d;
  }

  public int year() { return year; }
  public int month() { return month; }
  public int day() { return day; }
}