# Contributing to AURA

Thank you for your interest in contributing to AURA! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment.

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.8+
- MariaDB 10.6+ or MySQL 8+
- Docker (optional)

### Local Development Setup

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/alovoa.git
   cd alovoa
   ```

2. **Set up the database**
   ```bash
   # Using Docker
   docker run -d --name alovoa-db \
     -e MARIADB_ROOT_PASSWORD=root \
     -e MARIADB_DATABASE=alovoa \
     -e MARIADB_USER=alovoa \
     -e MARIADB_PASSWORD=alovoa \
     -p 3306:3306 mariadb:10.11
   ```

3. **Configure application.properties**
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   # Edit with your database credentials
   ```

4. **Build and run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

5. **Run tests**
   ```bash
   mvn test
   ```

## Development Workflow

### Branch Naming

- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation changes
- `refactor/` - Code refactoring
- `test/` - Test additions or fixes

### Commit Messages

Use conventional commits format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code restructuring
- `test`: Adding tests
- `chore`: Maintenance

### Pull Request Process

1. Create a feature branch from `master`
2. Make your changes with appropriate tests
3. Ensure all tests pass: `mvn verify`
4. Update documentation if needed
5. Submit a PR with a clear description

## Code Standards

### Java Style

- Follow Google Java Style Guide
- Use meaningful variable and method names
- Add Javadoc for public methods
- Keep methods focused and small

### Testing

- Write unit tests for new features
- Maintain test coverage
- Use meaningful test names with `@DisplayName`
- Follow AAA pattern (Arrange, Act, Assert)

### Security

- Never commit credentials or secrets
- Validate all user input
- Use parameterized queries
- Follow OWASP guidelines

## Project Structure

```
src/
├── main/
│   ├── java/com/nonononoki/alovoa/
│   │   ├── config/         # Configuration classes
│   │   ├── entity/         # JPA entities
│   │   ├── html/           # Page controllers
│   │   ├── model/          # DTOs and models
│   │   ├── repo/           # Repositories
│   │   ├── rest/           # REST controllers
│   │   └── service/        # Business logic
│   └── resources/
│       ├── db/migration/   # Flyway migrations
│       └── templates/      # Thymeleaf templates
└── test/
    └── java/               # Test classes
```

## Feature Development

### Adding a New Entity

1. Create entity in `entity/`
2. Create repository in `repo/`
3. Create service in `service/`
4. Create REST controller in `rest/`
5. Add database migration in `db/migration/`
6. Add tests

### Adding a New Page

1. Create HTML template in `templates/`
2. Create page controller in `html/`
3. Add navigation links

## Questions?

- Open an issue for bugs or feature requests
- Join discussions for questions
- Check existing issues before creating new ones

Thank you for contributing!
