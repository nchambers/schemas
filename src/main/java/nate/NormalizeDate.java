package nate;



public class NormalizeDate {

  NormalizeDate() {

  }

//   public DateTime normalize(String text, int docyear, int docmonth, int docday) {
//     text = text.toLowercase(text);
//     String words[] = text.split("\\s+");
//     int i = 0;


//     // Is the first word a month?
//     int month = normalizeMonth(words[i]);
//     int day = -1;
//     int year = -1;
//     if( month != -1 ) {
//       i++;
//       if( i < words.length ) {
// 	// try the day
// 	if( words[i].matches("\\d") ) {
// 	  day = normalizeDay(words[i]);
// 	  i++;
// 	}

// 	// try the year
// 	year = normalizeNumberAsYear(words[i]);
// 	if( year > -1 ) i++;
//       }
//     }

//     // Is the word a year?
//     if( i < words.length ) {
//       year = normalizeNumberAsYear(words[i]);
//       if( year > -1 ) i++;
//     }

//     // CREATE THE TIME POINT IF FOUND
//     // year given, no month (assume no day)
//     if( year > -1 && month == -1 )
//       cal = new GregorianCalendar(year,docmonth,1);
//     // year and month given
//     else if( year > -1 && month > -1 ) {
//       // day given
//       if( day > -1 ) cal = new GregorianCalendar(year,month,day);
//       else cal = new GregorianCalendar(year,month,1);
//     }
//     // no year, month and day
//     else if( year == -1 && month > -1 && day > -1 )
//       cal = new GregorianCalendar(docyear,month,day);
//     // no year, just month
//     else if( year == -1 && month > -1 )
//     cal = new GregorianCalendar(docyear,month,1);


//     // common relative times
//     if( i < words.length) {
//       if( words[i].equals("today") ) {
// 	GregorianCalendar cal = new GregorianCalendar(docyear, docmonth, docday);
// 	DateTime dt = new DateTime(cal);
//       } else if( words[i].equals("yesterday") ) {
// 	GregorianCalendar cal = new GregorianCalendar(docyear, docmonth, docday);
// 	cal.roll(Calendar.DAY_OF_MONTH,-1);
//       } else if( words[i].equals("tomorrow") ) {
// 	GregorianCalendar cal = new GregorianCalendar(docyear, docmonth, docday);
// 	cal.roll(Calendar.DAY_OF_MONTH,1);
//       } else if( words[i].equals("last") && i+1 < words.length ) {
// 	GregorianCalendar cal = new GregorianCalendar(docyear, docmonth, docday);
// 	i++;
// 	if( words[i].equals("week") ) cal.roll(Calendar.WEEK_OF_YEAR,-1);
// 	if( words[i].equals("month") ) cal.roll(Calendar.MONTH,-1);
// 	if( words[i].equals("year") ) cal.roll(Calendar.YEAR,-1);
// 	if( words[i].equals("quarter") ) cal.roll(Calendar.MONTH,-3);
//       } else if( words[i].equals("next") && i+1 < words.length ) {
// 	GregorianCalendar cal = new GregorianCalendar(docyear, docmonth, docday);
// 	i++;
// 	if( words[i].equals("week") ) cal.roll(Calendar.WEEK_OF_YEAR,-1);
// 	if( words[i].equals("month") ) cal.roll(Calendar.MONTH,1);
// 	if( words[i].equals("year") ) cal.roll(Calendar.YEAR,1);
// 	if( words[i].equals("quarter") ) cal.roll(Calendar.MONTH,3);
//       }
//     }

//     // Quantity of time durations?  "2 days" "3 quarters" "5 years"
//     if( numeral(words[i]) && i+1 < words.length ) {
//       int quantity = Integer.parseInt(words[i]);
//       i++;
//       int dayduration = timePeriod(words[i]);
//       dayduration *= quantity;

//       // "2 weeks ago"
//       if( i+2 < words.length && words[i+2].equals("ago") ) {
// 	cal = new GregorianCalendar(docyear, docmonth, docday);
// 	cal.roll(Calendar.DAY, -1 * dayduration);
//       }
//     }
    
//     DateTime dt = new DateTime(year, month, day);
//   }
  


//   private boolean numeral(String str) {
//     if( str.matches("\\d+") ) return true;
//     return false;
//   }


//   /**
//    * @return A number for the month, or -1 if not a month
//    */
//   public int normalizeMonth(String token) {
//     if( token.equals("january") || token.startsWith("jan") ) { return "01"; }
//     if( token.equals("february") || token.startsWith("feb") ) { return "02"; }
//     if( token.equals("march") || token.startsWith("mar") ) { return "03"; }
//     if( token.equals("april") || token.startsWith("apr") ) { return "04"; }
//     if( token.equals("may") ) { return "05"; }
//     if( token.equals("june") || token.startsWith("jun") ) { return "06"; }
//     if( token.equals("july") || token.startsWith("jul") ) { return "07"; }
//     if( token.equals("august") || token.startsWith("aug") ) { return "08"; }
//     if( token.equals("september") || token.startsWith("sep") ) { return "09"; }
//     if( token.equals("october") || token.startsWith("oct") ) { return "10"; }
//     if( token.equals("november") || token.startsWith("nov") ) { return "11"; }
//     if( token.equals("december") || token.startsWith("dec") ) { return "12"; }
//     return -1;
//   }


//   /**
//    * @return A number if the day is between 1-31, -1 otherwise
//    */
//   public int normalizeDay(String token) {
//     if( token.matches("\\d\\d?") ) {
//       int num = Integer.parseInt(token);
//       if( num < 32 ) return num;
//     }

//     if( token.equals("1st") ) return 1;
//     if( token.equals("2nd") ) return 2;
//     if( token.equals("3rd") ) return 3;
//     if( token.matches("\\dth") ) return normalizeDay(token.substring(0,1));
//     if( token.matches("\\d\\dth") ) return normalizeDay(token.substring(0,2));

//     return -1;
//   }


//   public int normalizeNumber(String token) {
//     // strip off the "th" in "20th"
//     if( token.endsWith("th") ) return normalizeNumber(token.substring(0,token.contains("th")));

//     // strip off apostrophe in "'98"
//     if( token.startsWith("'") ) return normalizeNumberAsYear(token.substring(1,token.length()));

//     // return 4-digit number as a year...
//     if( token.length() == 4 ) return Integer.parseInt(token);

//     // 2-digit numbers
//     if( token.length() <= 2 ) {
//       int num = Integer.parseInt(token);
//       if( num < 32 ) return num;
//       else return normalizeNumerAsYear(token);
//     }

//     else return -1;
//   }


//   /**
//    * @return A number if the token looks like a year, -1 otherwise
//    */
//   public int normalizeNumberAsYear(String token) {
//     // 1989
//     if( token.matches("\\d\\d\\d\\d") ) return Integer.parseInt(token);
//     // '89
//     if( token.matches("\'\\d\\d") ) return normalizeNumerAsYear(token.substring(1,3));
//     // 89
//     if( token.matches("\\d\\d") ) {
//       int num = Integer.parseInt(token);
//       if( num < 10 ) return 2000+num;
//       else return 1999+num;
//     }
//   }


//   public DateTime tomorrow(DateTime dt) {
//     if( dt.day
//   }

}
