const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const RULE_SOURCES = {
  rules: '../../libs/java-shared/base-rule-backend/src/main/resources/sample-rules/order-rules.yaml',
};

function loadYamlRules(relativePath) {
  const absolutePath = path.resolve(__dirname, relativePath);
  const { rules } = yaml.load(fs.readFileSync(absolutePath, 'utf8'));
  return rules;
}

module.exports = () => {
  const db = JSON.parse(fs.readFileSync(path.join(__dirname, 'db.json'), 'utf8'));
  const ruleCollections = Object.fromEntries(
    Object.entries(RULE_SOURCES).map(([key, file]) => [key, loadYamlRules(file)]),
  );
  return { ...db, ...ruleCollections };
};
