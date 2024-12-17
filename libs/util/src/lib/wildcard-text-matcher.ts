export function wildcardTextMatcher(str: string, rule: string) {
  // for this solution to work on any string, no matter what characters it has
  const escapeRegex = (str: string) => str.replace('/([.*+?^=!:${}()|[]/\\])/g', '\\$1');

  // "."  => Find a single character, except newline or line terminator
  // ".*" => Matches any string that contains zero or more characters
  rule = rule.split('*').map(escapeRegex).join('.*');

  // "^"  => Matches any string with the following at the beginning of it
  // "$"  => Matches any string with that in front at the end of it
  rule = '^' + rule + '.*';

  //Create a regular expression object for matching string
  const regex = new RegExp(rule);

  //Returns true if it finds a match, otherwise it returns false
  return regex.test(str);
}
