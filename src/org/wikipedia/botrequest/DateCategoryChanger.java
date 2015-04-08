package org.wikipedia.botrequest;

import java.io.File;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.wikipedia.Wiki;
import org.wikipedia.login.Login;
import org.wikiutils.ParseUtils;

public class DateCategoryChanger {
  private static Wiki wiki = new Wiki("fr.wikipedia.org");
  private static String comment = "";
  private static File file = new File("errors.txt");
  private static File ignore = new File("ignore.txt");
  private static HashSet<String> errors = new HashSet<>();
  private static List<String> ignoreList;

  private static Options options = new Options();
  private static int bornYear = 0;
  private static int deadYear = 0;
  private static int endYear = 0;

  private static boolean test = false;
  private static Logger logger;
  private static FileHandler logHandler;
  private static boolean nodebug = false;
  private static String testFile;

  public static void main(String[] args) throws IOException, LoginException {
    options.addOption("n", "naissance", true,
        "L'année de début pour les catégories de date de naissances");
    options.addOption("m", "mort", true, "L'année de début pour les catégories de date de décès");
    options.addOption("f", "fin", true, "L'année d'arrêt");
    options.addOption("t", "test", false, "Ne pas modifier les articles");
    options.addOption("d", "nodebug", false, "Désactiver le loggin");
    options.addOption("file", "file", true, "Fichier de test");
    CommandLineParser parser = new BasicParser();
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("d"))
        nodebug = true;
      else
        nodebug = false;
      initLogFile();
      if (cmd.hasOption("n"))
        bornYear = Integer.valueOf(cmd.getOptionValue("n"));
      if (cmd.hasOption("m"))
        deadYear = Integer.valueOf(cmd.getOptionValue("m"));
      if (cmd.hasOption("f"))
        endYear = Integer.valueOf(cmd.getOptionValue("f"));
      if (cmd.hasOption("t")) {
        test = true;
        if (cmd.hasOption("file")) {
          File path = new File(cmd.getOptionValue("file"));
          log(Level.INFO, "Reading content of " + path.getAbsolutePath());
          testFile = FileUtils.readFileToString(path);
        }
      }
    } catch (ParseException e) {
      help();
      return;
    }
    if (!test) {
      Login login = new Login();
      wiki.login(login.getBotLogin(), login.getBotPassword());
      ignoreList = Arrays.asList(FileUtils.readFileToString(ignore).split("\\n"));
      wiki.setMarkBot(true);
      wiki.setMarkMinor(true);
    }
    process();
    saveErrors();
  }

  private static void initLogFile() {
    logger = Logger.getLogger("DateCategoryChange");
    logger.setLevel(Level.ALL);
    if (nodebug)
      return;
    try {
      logHandler = new FileHandler("log", 1024 * 1024 * 1024 * 1024, 1, true);
      logger.addHandler(logHandler);
    } catch (SecurityException | IOException e) {

    }

  }

  private static void help() {
    HelpFormatter formater = new HelpFormatter();

    formater.printHelp("Main", options);
    System.exit(0);
  }

  private static void saveErrors() throws IOException {
    String[] list = FileUtils.readFileToString(file).split("\\n");
    String[] arrayOfString1 = list;
    int j = list.length;
    for (int i = 0; i < j; i++) {
      String str = arrayOfString1[i];
      errors.add(str);
    }
    FileUtils.writeStringToFile(file, errors.toString().replaceAll(", ", "\n"), false);
  }

  private static void process() throws IOException, LoginException {
    Scanner sc = new Scanner(System.in);
    if (test) {
      if (bornYear != 0) {
        process(bornYear, 2);
      } else {
        if (deadYear != 0)
          process(deadYear, 1);
        else {
          test(sc);
          sc.close();
        }
      }
    } else {
      if ((bornYear == 0) && (deadYear == 0)) {
        System.out.println("1 - Décès en \n2 - Naissance en\nVotre Choix : ");
        int type = sc.nextInt();
        System.out.println("Année de début : ");
        int year = sc.nextInt();
        sc.close();
        process(year, type);
      } else {
        if (bornYear != 0) {
          process(bornYear, 2);
        }
        if (deadYear != 0)
          process(deadYear, 1);
      }
    }
  }

  private static void process(int year, int type) throws IOException, LoginException {
    if (endYear == 0)
      endYear = Calendar.getInstance().get(Calendar.YEAR);
    for (; year < endYear; year++) {
      String category = "";
      if (type == 1)
        category = "Décès en " + year;
      else
        category = "Naissance en " + year;
      String[] articles = wiki.getCategoryMembers(category, new int[] { 0 });
      Arrays.sort(articles);
      processArticles(articles, year, type);
    }
  }

  private static void test(Scanner sc) throws LoginException, IOException {

    if (testFile == null) {
      do {
        System.out.println("Enter article title to test (exitBot to exit): ");
        String title = sc.nextLine();
        System.out.println("Year:");
        int year = sc.nextInt();
        System.out.println("Enter 1 if the year is a dead category 0 otherwise:");
        int type = sc.nextInt();
        processArticle(title, year, type);
        System.out.println("Enter article title to test (exitBot to exit): ");
      } while (!sc.next().equals("exitBot"));
    } else {
      changeBornCategory(testFile, null);
      changeDeadCategory(testFile, null);
    }

  }

  private static void processArticles(String[] articles, int year, int type) throws LoginException,
      IOException {
    for (int i = 0; i < articles.length; i++) {
      log(Level.INFO, i + "/" + articles.length + " : " + articles[i]);
      processArticle(articles[i], year, type);
      if ((i % 10 == 0) && (errors.size() != 0))
        saveErrors();
    }
  }

  private static void processArticle(String title, int year, int type) throws IOException,
      LoginException {
    if (!test && ignoreList.contains(title))
      return;
    String text = wiki.getPageText(title);
    String newText = "";
    comment = "";
    if (type == 1) {
      newText = changeDeadCategory(text, String.valueOf(year));
      newText = changeBornCategory(newText, null);
    } else {
      newText = changeBornCategory(text, String.valueOf(year));
      newText = changeDeadCategory(newText, null);
    }
    if (!test && !newText.equals(text)) {
      text = beforeSave(newText);
      try {
        wiki.fastEdit(title, text, comment);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private static String changeBornCategory(String text, String year) throws IOException {
    String[] bornRegex = new String[7];
    bornRegex[0] = "([Nn]ée?) le.*?(\\d\\d?|1er}?}?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    bornRegex[1] = "([Nn]ée?) le .*?(\\{\\{1er)( )([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|)(\\}\\} \\[?\\[?)(\\d\\d\\d\\d)";

    bornRegex[2] = "([Nn]ée?) à .*? le .*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    bornRegex[3] = "(''') \\([^)]*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]\\] \\[\\[| |\\|)(\\d\\d\\d\\d)";

    bornRegex[4] = "naissance\\s*?(=).*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    bornRegex[5] = "([Nn]ée?) (en) ([^\\]]*?)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    bornRegex[6] = "([Nn]ée?) à .*? le .*?(1e?r?}?}?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\||\\}\\} \\[?\\[?)(\\d\\d\\d\\d)";

    boolean found = false;
    if (year == null) {
      year = getYear(text, "Naissance en");
    }
    if (year == null) {
      log(Level.INFO, "Couldn't find birth date");
      return text;
    }
    log(Level.INFO, "Birth date is " + year);
    String oldText = ParseUtils.removeCommentsAndNoWikiText(text);
    for (String regex : bornRegex) {
      log(Level.INFO, "Using regex: " + regex);
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(oldText);
      if (matcher.find()) {
        log(Level.INFO, "Find match: " + matcher.group());
        if (matcher.group().length() > 100) {
          log(Level.WARNING, "Match too big !");
        }
        String month = getMonth(matcher.group(4));
        if ((matcher.group(6).equals(String.valueOf(year))) && (month != null)) {
          if (!wiki.exists("Catégorie:Naissance en " + month.toLowerCase() + " " + year)) {
            log(Level.WARNING, "La catégorie : Naissance en " + month.toLowerCase() + " " + year
                + " n'existe pas !");
            return text;
          }

          String oldCategory = "[[Catégorie:Naissance en " + year;
          String newCategory = "[[Catégorie:Naissance en " + month.toLowerCase() + " " + year;
          log(Level.INFO, "Old Category: " + oldCategory);
          log(Level.INFO, "New Category: " + newCategory);
          text = text.replace(oldCategory, newCategory);
          oldCategory = "[[catégorie:naissance en " + year;
          text = text.replace(oldCategory, newCategory);

          comment = comment + " Catégorie:Naissance en " + year + " --> "
              + "Catégorie:Naissance en " + month.toLowerCase() + " " + year;
          return text;
        } else {
          log(Level.WARNING, "Year: " + matcher.group(6) + " Month: " + month);
        }

      } else {
        log(Level.INFO, "No match found with this regex");
      }

    }

    if (!found) {
      log(Level.INFO, "No death date found using regular expression, trying with templates");
      ArrayList<String> templates = ParseUtils.getTemplates("Date de naissance", text);
      templates.addAll(ParseUtils.getTemplates("naissance décès âge", text));

      if ((templates.size() == 1) || (allEquals(templates))) {
        String template = (String) templates.get(0);
        String year1 = getBornYearFromTemplate(template);
        String month = getMonth(getBornMonthFromTemplate(template));
        if ((month != null) && (year1 != null) && (year1.trim().equals(String.valueOf(year)))) {
          if (!wiki.exists("Catégorie:Naissance en " + month.toLowerCase() + " " + year)) {
            System.out.println("La catégorie : Naissance en " + month.toLowerCase() + " " + year
                + " n'existe pas !");
            return text;
          }

          String oldCategory = "[[Catégorie:Naissance en " + year;
          String newCategory = "[[Catégorie:Naissance en " + month.toLowerCase() + " " + year;
          log(Level.INFO, "Old Category: " + oldCategory);
          log(Level.INFO, "New Category: " + newCategory);
          text = text.replace(oldCategory, newCategory);
          oldCategory = "[[catégorie:Naissance en " + year;
          text = text.replace(oldCategory, newCategory);

          comment = comment + " Catégorie:Naissance en " + year + " --> "
              + "Catégorie:Naissance en " + month.toLowerCase() + " " + year;
          return text;
        } else {
          log(Level.WARNING, "month: " + month + " year: " + year1 + "real year is: " + year);
        }
      } else {
        log(Level.INFO, "No or Many templates found!");
        log(Level.INFO, templates);
      }
    }
    return text;
  }

  private static String getBornMonthFromTemplate(String template) {
    String name = ParseUtils.getTemplateName(template);
    switch (name) {
    case "Date de naissance":
    case "date de naissance":
      return ParseUtils.getTemplateParam(template, 2);
    case "Naissance décès âge":
    case "naissance décès âge":
      return ParseUtils.getTemplateParam(template, 3);
    }
    return null;
  }

  private static String getBornYearFromTemplate(String template) {
    String name = ParseUtils.getTemplateName(template);
    switch (name) {
    case "Date de naissance":
    case "date de naissance":
      return ParseUtils.getTemplateParam(template, 3);
    case "Naissance décès âge":
    case "naissance décès âge":
      return ParseUtils.getTemplateParam(template, 4);
    }
    return null;
  }

  private static String getDeathMonthFromTemplate(String template) {
    String name = ParseUtils.getTemplateName(template);
    switch (name) {
    case "Date de décès":
    case "date de décès":
      return ParseUtils.getTemplateParam(template, 2);
    case "Naissance décès âge":
    case "naissance décès âge":
      return ParseUtils.getTemplateParam(template, 6);
    }
    return null;
  }

  private static String getDeathYearFromTemplate(String template) {
    String name = ParseUtils.getTemplateName(template);
    switch (name) {
    case "Date de décès":
    case "date de décès":
      return ParseUtils.getTemplateParam(template, 3);
    case "Naissance décès âge":
    case "naissance décès âge":
      return ParseUtils.getTemplateParam(template, 7);
    }
    return null;
  }

  private static String changeDeadCategory(String text, String year) throws IOException {
    if (year == null) {
      year = getYear(text, "Décès en");
    }
    if (year == null) {
      log(Level.INFO, "Couldn't find death date");
      return text;
    }
    log(Level.INFO, "Death date is " + year);
    boolean found = false;
    String[] deadRegex = new String[7];
    deadRegex[0] = "([Mm]orte?|[Dd]écédée?) le.*?(\\d\\d?|1er}?}?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    deadRegex[1] = "([Mm]orte?|[Dd]écédée?) le .*?(\\{\\{1er)( )([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|)(\\}\\} \\[?\\[?)(\\d\\d\\d\\d)";

    deadRegex[2] = "([Mm]orte?|[Dd]écédée?) à .*? le .*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    deadRegex[3] = "décès\\s*?(=).*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    deadRegex[4] = "([Mm]orte?|décédée?) (en) ([^\\]]*?)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    deadRegex[5] = "\\(([^\\)\\.]*?)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)?(\\]?\\]? \\[\\[| |\\|)?\\d\\d\\d\\d[^\\)\\.]*?([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)[^\\)\\.]*?\\)";

    deadRegex[6] = "mort\\s*?(=).*?(\\d\\d?)( |\\|)([jJ]anvier|[fF]évrier|[mM]ars|[Aa]vril|[Mm]ai|[Jj]uin|[Jj]uillet|[Aa]o[uû]t|[Ss]eptembre|[Oo]ctobre|[Nn]ovembre|[Dd]écembre|\\d\\d?)(\\]?\\]? \\[\\[| |\\|)(\\d\\d\\d\\d)";

    String oldText = ParseUtils.removeCommentsAndNoWikiText(text);
    for (String regex : deadRegex) {
      log(Level.INFO, "Using regex: " + regex);
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(oldText);
      if (matcher.find()) {
        log(Level.INFO, matcher.group());
        if (matcher.group().length() > 100) {
          log(Level.WARNING, "Error! match too big");
        }
        String month = getMonth(matcher.group(4));
        if ((matcher.group(6).equals(String.valueOf(year))) && (month != null)) {
          if (!wiki.exists("Catégorie:Décès en " + month.toLowerCase() + " " + year)) {
            log(Level.WARNING, "La catégorie : Décès en " + month.toLowerCase() + " " + year
                + " n'existe pas !");
            return text;
          }

          String oldCategory = "[[Catégorie:Décès en " + year;
          String newCategory = "[[Catégorie:Décès en " + month.toLowerCase() + " " + year;
          log(Level.INFO, "Old Category: " + oldCategory);
          log(Level.INFO, "New Category: " + newCategory);
          text = text.replace(oldCategory, newCategory);
          oldCategory = "[[catégorie:décès en " + year;
          text = text.replace(oldCategory, newCategory);

          comment = comment + " Catégorie:Décès en " + year + " --> " + "Catégorie:Décès en "
              + month.toLowerCase() + " " + year;
          return text;
        }

      } else {
        log(Level.INFO, "No match found with this regex");
      }

    }

    if (!found) {
      log(Level.INFO, "No death date found using regular expression, trying with templates");
      ArrayList<String> templates = ParseUtils.getTemplates("Date de décès", text);
      templates.addAll(ParseUtils.getTemplates("Naissance décès âge", text));
      if ((templates.size() == 1) || (allEquals(templates))) {
        String template = (String) templates.get(0);
        String year1 = getDeathYearFromTemplate(template);
        String month = getMonth(getDeathMonthFromTemplate(template));
        if ((month != null) && (year1 != null)
            && (year1.trim().equals(String.valueOf(year).trim()))) {
          String oldCategory = "[[Catégorie:Décès en " + year;
          String newCategory = "[[Catégorie:Décès en " + month.toLowerCase() + " " + year;
          log(Level.INFO, "Old Category: " + oldCategory);
          log(Level.INFO, "New Category: " + newCategory);
          if (!wiki.exists("Catégorie:Décès en " + month.toLowerCase() + " " + year)) {
            log(Level.WARNING, "La catégorie : Décès en " + month.toLowerCase() + " " + year
                + " n'existe pas !");
            return text;
          }
          text = text.replace(oldCategory, newCategory);
          oldCategory = "[[catégorie:Décès en " + year;
          text = text.replace(oldCategory, newCategory);

          comment = comment + " Catégorie:Décès en " + year + " --> " + "Catégorie:Décès en "
              + month.toLowerCase() + " " + year;
          return text;
        } else {
          log(Level.WARNING, "month: " + month + " year: " + year1 + " (expected: " + year + ")");
        }
      } else {
        log(Level.INFO, "No or Many templates found !");
        log(Level.INFO, templates);
      }
    }
    return text;
  }

  private static void log(Level level, Object object) {
    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
    StackTraceElement e = stacktrace[2];// maybe this number needs to be corrected
    String method = e.getMethodName();
    logger.logp(level, "Wiki", method, "[{0}] {1}", new Object[] { "wiki", object });
  }

  private static String getYear(String text, String categ) {
    String regex = "\\[\\[catégorie:" + categ + " (\\d\\d\\d\\d)";
    regex = regex.toLowerCase();
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(text.toLowerCase());
    if (matcher.find())
      return matcher.group(1);
    return null;
  }

  private static boolean allEquals(ArrayList<String> templates) {
    if (templates.size() == 0)
      return false;
    String template = (String) templates.get(0);
    log(Level.INFO, template);
    String day = ParseUtils.getTemplateParam(template, 1);
    String month = getMonth(ParseUtils.getTemplateParam(template, 2));
    String year = ParseUtils.getTemplateParam(template, 3);
    for (int i = 1; i < templates.size(); i++) {
      template = (String) templates.get(i);
      log(Level.INFO, template);
      String day2 = ParseUtils.getTemplateParam(template, 1);
      String month2 = getMonth(ParseUtils.getTemplateParam(template, 2));
      String year2 = ParseUtils.getTemplateParam(template, 3);
      if ((day != null) && (!day.equals(day2)))
        return false;
      if ((month != null) && (!month.equals(month2)))
        return false;
      if ((year != null) && (!year.equals(year2))) {
        return false;
      }
    }
    return true;
  }

  private static String getMonth(String str) {
    if (str == null)
      return null;
    str = str.toLowerCase().trim();
    if (str.equals("aout"))
      return "août";
    if (str.length() > 9)
      return null;
    DateFormatSymbols dfs = new DateFormatSymbols();
    if (str.length() > 2)
      for (int i = 0; i < 12; i++)
        if (dfs.getMonths()[i].equals(str))
          return str;
    try {
      int m = Integer.parseInt(str);
      if ((m > 0) && (m < 13))
        return dfs.getMonths()[(m - 1)];
    } catch (Exception e) {
      return null;
    }
    return null;
  }

  private static String beforeSave(String text) {
    text = text.replace("{{pdf}} {{lien web", "{{lien web|format=pdf");
    text = text.replace("{{Pdf}} {{lien web", "{{lien web|format=pdf");
    text = text.replace("{{Pdf}} {{Lien web", "{{Lien web|format=pdf");
    text = text.replace("{{pdf}} {{Lien web", "{{Lien web|format=pdf");
    text = text.replace("{{en}} {{lien web", "{{lien web|langue=en");
    text = text.replace("{{en}} {{Lien web", "{{Lien web|langue=en");
    return text;
  }
}