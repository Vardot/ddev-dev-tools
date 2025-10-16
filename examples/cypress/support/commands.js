
Cypress.Commands.add("drupalLogin", (username, password) => {
    cy.session([username, password], () => {
        cy.visit('/user/login');
        cy.get('input[name="name"]').type(username, { log: false });
        cy.get('input[name="pass"]').type(password, { log: false });
        cy.get('#edit-submit').click();
        cy.url().should('not.include', '/user/login');
        cy.get('body').should('not.contain', 'Unrecognized username or password');
    });
    cy.visit('/user');
});
